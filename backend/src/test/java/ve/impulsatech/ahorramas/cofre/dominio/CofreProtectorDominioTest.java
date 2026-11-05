package ve.impulsatech.ahorramas.cofre.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;

/** Reglas de los cuatro engranajes del Cofre Protector (R-08 a R-12). */
class CofreProtectorDominioTest {

    static final ZoneOffset VET = ZoneOffset.ofHours(-4);
    static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 11, 4, 10, 0, 0, 0, VET);
    static final TasaBcv TASA = new TasaBcv(LocalDate.of(2026, 11, 4), 4_987_321);

    static MetaAhorro meta(long objetivo, LocalDate fecha, String frecuencia) {
        MetaAhorro m = MetaAhorro.crear(1, "Viaje en marzo", "viaje", objetivo, fecha, frecuencia, AHORA);
        return new MetaAhorro(10L, m.idUsuario(), m.nombre(), m.categoria(), m.montoObjetivoUsdCts(), m.fechaObjetivo(),
                m.frecuenciaCuota(), m.fechaCreacion(), m.estatus(), null);
    }

    static Aporte asiento(String tipo, long usd, LocalDate fecha) {
        return new Aporte(null, 10, null, null, null, fecha.atTime(12, 0).atOffset(VET), tipo, "manual", usd, 0, 1, null);
    }

    @Nested
    class Metas {
        @Test
        void laFechaObjetivoDebeSerFutura() {
            assertThatThrownBy(() -> MetaAhorro.crear(1, "Ya", "viaje", 100, AHORA.toLocalDate(), "diaria", AHORA))
                    .isInstanceOf(ErrorNegocio.class).hasMessageContaining("posterior");
        }

        @Test
        void elAcumuladoSeCalculaDelLedger() {
            List<Aporte> l = List.of(asiento("aporte", 500, LocalDate.of(2026, 11, 1)), asiento("aporte", 300, LocalDate.of(2026, 11, 2)),
                    asiento("retiro", 200, LocalDate.of(2026, 11, 3)));
            assertThat(Aporte.acumulado(l)).isEqualTo(600);
        }

        @Test
        void progresoEnPuntosBasicosConTope() {
            MetaAhorro m = meta(30_000, LocalDate.of(2027, 3, 15), "diaria");
            assertThat(m.progresoPb(1_200)).isEqualTo(400);
            assertThat(m.progresoPb(99_999)).isEqualTo(10_000);
        }
    }

    @Nested
    class RutaDelDiaR09 {
        @Test
        void bajaLaMetaAUnaCuotaDiariaConvertidaABs() {
            // $300 a 131 días (04-nov → 15-mar) → ⌈30000/131⌉ = 230 cts = $2,30
            MetaAhorro m = meta(30_000, LocalDate.of(2027, 3, 15), "diaria");
            var c = RutaDelDia.calcular(m, 0, LocalDate.of(2026, 11, 4), TASA);
            assertThat(c.periodosRestantes()).isEqualTo(131);
            assertThat(c.montoUsdCts()).isEqualTo(230);
            assertThat(c.montoBsCts()).isEqualTo(TASA.usdABs(230));
            assertThat(RutaDelDia.mensaje(m.nombre(), c.montoUsdCts(), c.montoBsCts(), "diaria", "pendiente"))
                    .startsWith("Hoy toca apartar $2,30 (Bs 1.147,08)");
        }

        @Test
        void cuotaSemanalUsaElLunesYRestaLoAcumulado() {
            MetaAhorro m = meta(10_000, LocalDate.of(2026, 12, 2), "semanal");
            var c = RutaDelDia.calcular(m, 4_000, LocalDate.of(2026, 11, 4), TASA);   // miércoles
            assertThat(c.fechaPeriodo()).isEqualTo(LocalDate.of(2026, 11, 2));       // lunes
            assertThat(c.periodosRestantes()).isEqualTo(4);                          // 28 días → 4 semanas
            assertThat(c.montoUsdCts()).isEqualTo(1_500);                            // (100−40)/4
        }

        @Test
        void metaYaAlcanzadaNoPideCuota() {
            MetaAhorro m = meta(10_000, LocalDate.of(2026, 12, 2), "diaria");
            assertThat(RutaDelDia.calcular(m, 10_000, LocalDate.of(2026, 11, 4), TASA).montoUsdCts()).isZero();
            assertThat(RutaDelDia.estatus(0, 0)).isEqualTo("cumplida");
        }

        @Test
        void estatusSegunLoAportado() {
            assertThat(RutaDelDia.estatus(240, 0)).isEqualTo("pendiente");
            assertThat(RutaDelDia.estatus(240, 100)).isEqualTo("parcial");
            assertThat(RutaDelDia.estatus(240, 240)).isEqualTo("cumplida");
        }
    }

    @Nested
    class RachaConDiasColchonR11 {
        final List<Racha.Tramo> tramos = List.of(new Racha.Tramo(0, 100, 2), new Racha.Tramo(7, 125, 3), new Racha.Tramo(30, 150, 4));

        Racha racha(LocalDate inicio, String... tipos) {
            List<Racha.Dia> dias = new ArrayList<>();
            for (int i = 0; i < tipos.length; i++) {
                dias.add(new Racha.Dia(inicio.plusDays(i), tipos[i]));
            }
            return new Racha(1L, 1, inicio, null, null, dias);
        }

        @Test
        void unDiaSinAporteConsumeColchonEnVezDeRomperLaRacha() {
            Racha r = racha(LocalDate.of(2026, 11, 1), "aporte", "aporte");
            assertThat(r.evaluarDia(LocalDate.of(2026, 11, 3), tramos)).isEqualTo(Racha.Decision.USAR_COLCHON);
            assertThat(r.colchonesDisponibles(LocalDate.of(2026, 11, 3), tramos)).isEqualTo(2);
        }

        @Test
        void sinColchonesLaRachaSeCierra() {
            Racha r = racha(LocalDate.of(2026, 11, 1), "aporte", "colchon", "colchon");
            assertThat(r.colchonesDisponibles(LocalDate.of(2026, 11, 4), tramos)).isZero();
            assertThat(r.evaluarDia(LocalDate.of(2026, 11, 4), tramos)).isEqualTo(Racha.Decision.CERRAR);
        }

        @Test
        void losColchonesSeRenuevanCadaMes() {
            Racha r = racha(LocalDate.of(2026, 10, 30), "colchon", "colchon", "aporte");
            assertThat(r.colchonesDisponibles(LocalDate.of(2026, 11, 1), tramos)).isEqualTo(2);
        }

        @Test
        void elMultiplicadorSubeConLosTramos() {
            Racha r = racha(LocalDate.of(2026, 11, 1), "aporte", "aporte", "aporte", "aporte", "aporte", "aporte", "colchon");
            assertThat(r.diasConsecutivos()).isEqualTo(7);
            assertThat(r.tramo(tramos).multiplicadorX100()).isEqualTo(125);
            assertThat(Racha.siguienteTramo(7, tramos)).get().extracting(Racha.Tramo::diasMinimos).isEqualTo(30);
        }

        @Test
        void puntosConMultiplicador() {
            var p = PuntosPorAporte.calcular(240, true, 150);   // (10 + 2) × 1,5 = 18
            assertThat(p.base()).isEqualTo(12);
            assertThat(p.bonoRacha()).isEqualTo(6);
            assertThat(PuntosPorAporte.calcular(240, false, 100).total()).isEqualTo(2);
        }
    }

    @Nested
    class SelloR12 {
        @Test
        void soloSePuedeSellarLoQueYaEstaEnElCofre() {
            MetaAhorro m = meta(30_000, LocalDate.of(2027, 3, 15), "diaria");
            assertThatThrownBy(() -> SelloCompromiso.sellar(m, 1_500, 1_000, false, AHORA)).hasMessageContaining("hasta lo que ya tienes");
            SelloCompromiso s = SelloCompromiso.sellar(m, 1_000, 1_000, false, AHORA);
            assertThat(s.fechaLiberacion()).isEqualTo(m.fechaObjetivo());
        }

        @Test
        void unRetiroQueTocaLoSelladoAntesDeLaFechaLoRompe() {
            MetaAhorro m = meta(30_000, LocalDate.of(2027, 3, 15), "diaria");
            SelloCompromiso s = SelloCompromiso.sellar(m, 1_000, 1_500, false, AHORA);
            assertThat(s.seRompeCon(600, LocalDate.of(2026, 11, 10))).isTrue();
            assertThat(s.seRompeCon(1_000, LocalDate.of(2026, 11, 10))).isFalse();
            assertThat(s.seRompeCon(0, LocalDate.of(2027, 3, 15))).isFalse();   // ya liberado por fecha
        }
    }

    @Nested
    class EspejoDeImpacto {
        @Test
        void calculaElPoderDeCompraProtegidoFrenteALaDevaluacion() {
            // Tasa: 400 Bs/USD el 1.º de noviembre, 440 el 15 y 480 el 30 (devaluación de 20 % en el mes)
            java.util.function.Function<LocalDate, TasaBcv> tasaDe = f -> {
                long v = f.getDayOfMonth() < 15 ? 4_000_000 : f.getDayOfMonth() < 30 ? 4_400_000 : 4_800_000;
                return new TasaBcv(f, v);
            };
            List<Aporte> asientos = List.of(
                    asiento("aporte", 10_000, LocalDate.of(2026, 10, 20)),   // $100 desde antes del mes → 100 × 80 = Bs 8.000
                    asiento("aporte", 2_000, LocalDate.of(2026, 11, 15)));   // $20 desde el 15 → 20 × 40 = Bs 800
            var r = EspejoImpacto.calcular(asientos, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 30), tasaDe);
            assertThat(r.saldoCofresUsdCts()).isEqualTo(12_000);
            assertThat(r.perdidaEvitadaBsCts()).isEqualTo(880_000);         // Bs 8.800,00
            assertThat(r.perdidaEvitadaUsdCts()).isEqualTo(new TasaBcv(LocalDate.of(2026, 11, 30), 4_800_000).bsAUsd(880_000));
            assertThat(r.devaluacionPb()).isEqualTo(2_000);
            assertThat(r.frase()).isEqualTo("Tu cofre de $120,00 te protegió de perder Bs 8.800,00 este mes.");
        }

        @Test
        void unRetiroDejaDeEstarProtegidoDesdeEseDia() {
            java.util.function.Function<LocalDate, TasaBcv> tasaDe = f -> new TasaBcv(f, f.getDayOfMonth() < 15 ? 4_000_000 : 5_000_000);
            List<Aporte> asientos = List.of(asiento("aporte", 10_000, LocalDate.of(2026, 10, 1)),
                    asiento("retiro", 10_000, LocalDate.of(2026, 11, 1)));
            var r = EspejoImpacto.calcular(asientos, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 30), tasaDe);
            assertThat(r.saldoCofresUsdCts()).isZero();
            assertThat(r.perdidaEvitadaBsCts()).isZero();
        }

        @Test
        void sinCofreLaFraseInvitaAEmpezar() {
            var r = EspejoImpacto.calcular(List.of(), YearMonth.of(2026, 11), LocalDate.of(2026, 11, 4), f -> TASA);
            assertThat(r.frase()).startsWith("Aún no tienes dinero en tu cofre");
        }
    }

    @Nested
    class ReglasAutomaticasR13 {
        @Test
        void porcentajeDeIngresoConvertidoAUsd() {
            ReglaAhorro r = ReglaAhorro.aprobar(10, "pct_ingreso", 1_000, null, null, null, AHORA);
            // 10 % de Bs 49.873,21 ($100) = $10
            assertThat(r.aporteParaMovimiento("ingreso", 4_987_321, Moneda.VES, TASA)).isEqualTo(1_000);
            assertThat(r.aporteParaMovimiento("gasto", 4_987_321, Moneda.VES, TASA)).isZero();
        }

        @Test
        void redondeoApartaLaDiferenciaDelGasto() {
            ReglaAhorro r = ReglaAhorro.aprobar(10, "redondeo", null, 100_000L, null, null, AHORA);   // múltiplo Bs 1.000
            // Gasto de Bs 2.350 → se redondea a Bs 3.000 → se apartan Bs 650 = $1,30
            assertThat(r.aporteParaMovimiento("gasto", 235_000, Moneda.VES, TASA)).isEqualTo(TASA.bsAUsd(65_000));
            assertThat(r.aporteParaMovimiento("gasto", 300_000, Moneda.VES, TASA)).isZero();
        }

        @Test
        void validaLosAtributosDelSubtipo() {
            assertThatThrownBy(() -> ReglaAhorro.aprobar(10, "pct_ingreso", null, null, null, null, AHORA)).isInstanceOf(ErrorNegocio.class);
            assertThatThrownBy(() -> ReglaAhorro.aprobar(10, "fijo_quincenal", null, null, 0L, null, AHORA)).isInstanceOf(ErrorNegocio.class);
            assertThatThrownBy(() -> ReglaAhorro.aprobar(10, "otro", null, null, null, null, AHORA)).isInstanceOf(ErrorNegocio.class);
        }
    }
}
