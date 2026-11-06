package ve.impulsatech.ahorramas.pagos.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import ve.impulsatech.ahorramas.pagos.api.KpisApi.Bucket;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis.Calculo;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis.Mensaje;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis.Pago;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis.Transicion;

/** KPIs del prototipo: tasa de aprobación, latencia p95, picos de TPS y tasa de reversos. */
class CalculadoraKpisTest {

    static final ZoneId CARACAS = ZoneId.of("America/Caracas");
    static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 4, 10, 0, 0, 0, ZoneOffset.ofHours(-4));

    static Pago pago(long id, EstadoPago estado, long monto, OffsetDateTime creacion, boolean capturado) {
        return new Pago(id, estado, monto, 2_000, creacion, capturado ? creacion.plusSeconds(1) : null,
                estado == EstadoPago.RECHAZADO ? "Fondos insuficientes" : null, "S7B-" + id);
    }

    static Mensaje mensaje(long idPago, String tipo, OffsetDateTime envio, Long ms) {
        return new Mensaje(idPago, tipo, envio, ms == null ? null : envio.plusNanos(ms * 1_000_000), ms == null ? "timeout" : "aprobado", "00");
    }

    @Test
    void percentilPorRangoMasCercano() {
        long[] v = CalculadoraKpis.ordenadas(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000);
        assertThat(CalculadoraKpis.percentil(v, 50)).isEqualTo(500);
        assertThat(CalculadoraKpis.percentil(v, 95)).isEqualTo(1000);
        assertThat(CalculadoraKpis.percentil(v, 99)).isEqualTo(1000);
        assertThat(CalculadoraKpis.percentil(new long[0], 95)).isZero();
        assertThat(CalculadoraKpis.tasaPb(1, 0)).isNull();
        assertThat(CalculadoraKpis.tasaPb(19, 20)).isEqualTo(9_500);
    }

    @Test
    void clasificaAprobadosRechazadosYEnCurso() {
        assertThat(CalculadoraKpis.aprobado(pago(1, EstadoPago.CONCILIADO, 100, T0, true))).isTrue();
        assertThat(CalculadoraKpis.aprobado(pago(2, EstadoPago.REVERSADO, 100, T0, true))).isTrue();     // capturado y luego reversado
        assertThat(CalculadoraKpis.rechazado(pago(3, EstadoPago.REVERSADO, 100, T0, false))).isTrue();   // nunca se capturó
        assertThat(CalculadoraKpis.rechazado(pago(4, EstadoPago.RECHAZADO, 100, T0, false))).isTrue();
        assertThat(CalculadoraKpis.enCurso(pago(5, EstadoPago.EN_VERIFICACION, 100, T0, false))).isTrue();
    }

    @Test
    void calculaTasasLatenciasYPicosDeLaVentana() {
        List<Pago> pagos = List.of(
                pago(1, EstadoPago.CAPTURADO, 1_500_000, T0.plusMinutes(1), true),
                pago(2, EstadoPago.CONCILIADO, 1_500_000, T0.plusMinutes(1), true),
                pago(3, EstadoPago.REVERSADO, 1_500_099, T0.plusMinutes(2), true),
                pago(4, EstadoPago.RECHAZADO, 1_500_051, T0.plusMinutes(2), false),
                pago(5, EstadoPago.EN_VERIFICACION, 1_500_013, T0.plusMinutes(70), false));
        List<Mensaje> mensajes = List.of(
                mensaje(1, "autoriz", T0.plusMinutes(1), 200L), mensaje(1, "captura", T0.plusMinutes(1).plusSeconds(1), 300L),
                mensaje(2, "autoriz", T0.plusMinutes(1), 250L), mensaje(2, "captura", T0.plusMinutes(1).plusSeconds(1), 350L),
                mensaje(3, "autoriz", T0.plusMinutes(2), 900L), mensaje(3, "captura", T0.plusMinutes(2).plusSeconds(1), 400L),
                mensaje(3, "reverso", T0.plusMinutes(3), 150L),
                mensaje(4, "autoriz", T0.plusMinutes(2), 180L),
                mensaje(5, "autoriz", T0.plusMinutes(70), null));
        List<Transicion> transiciones = List.of(
                new Transicion(1, null, EstadoPago.PENDIENTE, T0.plusMinutes(1), "usuario"),
                new Transicion(1, EstadoPago.PENDIENTE, EstadoPago.AUTORIZADO, T0.plusMinutes(1).plusSeconds(1), "suiche"),
                new Transicion(1, EstadoPago.AUTORIZADO, EstadoPago.CAPTURADO, T0.plusMinutes(1).plusSeconds(2), "suiche"),
                new Transicion(3, EstadoPago.CAPTURADO, EstadoPago.CON_DIFERENCIA, T0.plusMinutes(3), "sistema"),
                new Transicion(3, EstadoPago.CON_DIFERENCIA, EstadoPago.REVERSADO, T0.plusMinutes(3).plusSeconds(1), "suiche"),
                new Transicion(4, EstadoPago.PENDIENTE, EstadoPago.RECHAZADO, T0.plusMinutes(2).plusSeconds(1), "suiche"));

        Calculo c = CalculadoraKpis.calcular(T0, T0.plusHours(2), Duration.ofHours(1), CARACAS, pagos, mensajes, transiciones);
        var r = c.resumen();
        assertThat(r.pagos()).isEqualTo(5);
        assertThat(r.aprobados()).isEqualTo(3);
        assertThat(r.rechazados()).isEqualTo(1);
        assertThat(r.enCurso()).isEqualTo(1);
        assertThat(r.tasaAprobacionPb()).isEqualTo(7_500);           // 3 de 4 terminales
        assertThat(r.tasaReversosPb()).isEqualTo(3_333);             // 1 de 3 aprobados
        assertThat(r.timeouts()).isEqualTo(1);
        assertThat(r.tasaTimeoutsPb()).isEqualTo(1_111);             // 1 de 9 mensajes
        assertThat(r.latenciaSuiche().muestras()).isEqualTo(8);
        assertThat(r.latenciaSuiche().p95Ms()).isEqualTo(900);
        assertThat(r.latenciaSuiche().p50Ms()).isEqualTo(250);
        assertThat(r.latenciaExtremoAExtremo().muestras()).isEqualTo(3);
        assertThat(r.latenciaExtremoAExtremo().p95Ms()).isEqualTo(61_000);   // pago 3: creación 10:02 → REVERSADO 10:03:01
        assertThat(r.pagosSegundoPico()).isEqualTo(2);
        assertThat(r.pagosMinutoPico()).isEqualTo(2);
        assertThat(r.montoTotalCts()).isEqualTo(7_500_163);
        assertThat(r.comisionTotalCts()).isEqualTo(6_000);           // rechazado y reversado no cobran (R-17)

        assertThat(c.latenciasPorTipo()).extracting("tipo").containsExactly("autoriz", "captura", "reverso");
        assertThat(c.latenciasPorTipo().get(0).timeouts()).isEqualTo(1);
        assertThat(c.origenReversos()).extracting("clave").containsExactly("CON_DIFERENCIA");
        assertThat(c.motivosRechazo()).extracting("clave").containsExactly("Fondos insuficientes");
        assertThat(c.estados()).extracting("clave").containsExactly("CAPTURADO", "CONCILIADO", "RECHAZADO", "EN_VERIFICACION", "REVERSADO");

        List<Bucket> s = c.series();
        assertThat(s).hasSize(2);
        assertThat(s.get(0).inicio()).isEqualTo(T0);
        assertThat(s.get(0).pagos()).isEqualTo(4);
        assertThat(s.get(0).tasaAprobacionPb()).isEqualTo(7_500);
        assertThat(s.get(0).p95Ms()).isEqualTo(900);
        assertThat(s.get(1).pagos()).isEqualTo(1);
        assertThat(s.get(1).enCurso()).isEqualTo(1);
        assertThat(s.get(1).timeouts()).isEqualTo(1);
        assertThat(s.get(1).p95Ms()).isNull();

        assertThat(c.pagosRecientes()).hasSize(5);
        assertThat(c.pagosRecientes().get(0).idPago()).isEqualTo(5);
        assertThat(c.pagosRecientes().get(0).escenario()).isEqualTo(13);
        assertThat(c.pagosRecientes().get(0).latenciaAutorizacionMs()).isNull();
        assertThat(c.pagosRecientes().get(4).latenciaAutorizacionMs()).isEqualTo(200);
        assertThat(c.pagosRecientes().get(4).latenciaTotalMs()).isEqualTo(2_000);
    }

    @Test
    void laSeriePorDiaSeAlineaAlDiaLocalYElBucketAutomaticoDependeDeLaVentana() {
        OffsetDateTime desde = OffsetDateTime.of(2026, 11, 1, 23, 30, 0, 0, ZoneOffset.ofHours(-4));
        Calculo c = CalculadoraKpis.calcular(desde, desde.plusDays(3), Duration.ofDays(1), CARACAS,
                List.of(pago(1, EstadoPago.CAPTURADO, 100, desde.plusMinutes(10), true), pago(2, EstadoPago.CAPTURADO, 100, desde.plusHours(2), true)),
                List.of(), List.of());
        assertThat(c.series()).hasSize(4);
        assertThat(c.series().get(0).inicio().toLocalDate().toString()).isEqualTo("2026-11-01");
        assertThat(c.series().get(0).pagos()).isEqualTo(1);
        assertThat(c.series().get(1).pagos()).isEqualTo(1);
        assertThat(CalculadoraKpis.bucketAutomatico(Duration.ofHours(1))).isEqualTo(Duration.ofMinutes(1));
        assertThat(CalculadoraKpis.bucketAutomatico(Duration.ofHours(24))).isEqualTo(Duration.ofHours(1));
        assertThat(CalculadoraKpis.bucketAutomatico(Duration.ofDays(7))).isEqualTo(Duration.ofDays(1));
    }
}
