package ve.impulsatech.ahorramas.pagos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.pagos.dominio.Conciliador.PagoAConciliar;
import ve.impulsatech.ahorramas.pagos.dominio.Conciliador.RegistroLiquidacion;
import ve.impulsatech.ahorramas.pagos.dominio.Conciliador.Tipo;

/** Máquina de estados (R-14), comisión (R-17), puntualidad (R-18), límites y conciliación (R-19). */
class PagosDominioTest {

    static final OffsetDateTime AHORA = OffsetDateTime.of(2026, 11, 4, 10, 0, 0, 0, ZoneOffset.ofHours(-4));
    static final TarifaComision TARIFA = new TarifaComision(1, 2_000, 20);

    PagoTdc pago(long monto) {
        return PagoTdc.nuevo(1, 2, 3, null, TARIFA, "clave-" + monto, monto, true, AHORA).conId(99);
    }

    @Test
    void caminoFelizPendienteAutorizadoCapturadoConciliado() {
        PagoTdc p = pago(1_500_000).autorizado("AUT1", "S7B-1").capturado("S7B-1", AHORA, LocalDate.of(2026, 11, 12)).pasarA(EstadoPago.CONCILIADO);
        assertThat(p.estado()).isEqualTo(EstadoPago.CONCILIADO);
        assertThat(p.esPuntual()).isTrue();
    }

    @Test
    void noSePuedeSaltarEstados() {
        assertThatThrownBy(() -> pago(100).pasarA(EstadoPago.CAPTURADO)).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> pago(100).rechazado("x").pasarA(EstadoPago.AUTORIZADO)).isInstanceOf(ErrorNegocio.class);
        assertThat(EstadoPago.CONCILIADO.terminal()).isTrue();
        assertThat(EstadoPago.EN_VERIFICACION.puedePasarA(EstadoPago.AUTORIZADO)).isTrue();
    }

    @Test
    void comisionSeCalculaUnaVezYNoSeCobraSiSeRechaza() {
        PagoTdc p = pago(1_500_000);
        assertThat(p.comisionCts()).isEqualTo(2_000 + 3_000);   // Bs 20 + 0,20 %
        assertThat(p.rechazado("Fondos insuficientes").comisionCobrada()).isZero();
    }

    @Test
    void pagoTardioNoEsPuntual() {
        PagoTdc p = pago(100).autorizado("A", "S").capturado("S", AHORA, LocalDate.of(2026, 11, 3));
        assertThat(p.esPuntual()).isFalse();
    }

    @Test
    void exigeBeneficiarioConfirmadoYClave() {
        assertThatThrownBy(() -> PagoTdc.nuevo(1, 2, 3, null, TARIFA, "k", 100, false, AHORA)).hasMessageContaining("titular");
        assertThatThrownBy(() -> PagoTdc.nuevo(1, 2, 3, null, TARIFA, " ", 100, true, AHORA)).isInstanceOf(ErrorNegocio.class);
    }

    @Test
    void limiteDiarioYOperaciones() {
        assertThatThrownBy(() -> ControlesPago.validarLimite(1_000, 5, 900, 1, 200)).hasMessageContaining("límite diario");
        assertThatThrownBy(() -> ControlesPago.validarLimite(1_000, 2, 0, 2, 1)).hasMessageContaining("máximo");
        ControlesPago.validarLimite(1_000, 5, 800, 1, 200);
    }

    @Test
    void conciliaDetectaTodasLasDiferencias() {
        List<PagoAConciliar> pagos = List.of(
                new PagoAConciliar(1, "k1", "S1", 100, EstadoPago.CAPTURADO),    // conciliado
                new PagoAConciliar(2, "k2", "S2", 100, EstadoPago.CAPTURADO),    // monto distinto
                new PagoAConciliar(3, "k3", "S3", 100, EstadoPago.CAPTURADO),    // falta en el suiche
                new PagoAConciliar(4, "k4", null, 100, EstadoPago.RECHAZADO));   // rechazado y reversado en el suiche
        List<RegistroLiquidacion> registros = List.of(
                new RegistroLiquidacion(1, "S1", "k1", 100, "capturado"),
                new RegistroLiquidacion(2, "S2", "k2", 200, "capturado"),
                new RegistroLiquidacion(3, "S4", "k4", 100, "reversado"),
                new RegistroLiquidacion(4, "S9", "k9", 50, "capturado"));       // falta en Ahorra+
        var r = Conciliador.conciliar(pagos, registros);
        assertThat(r).extracting(Conciliador.Resultado::tipo)
                .containsExactly(Tipo.conciliado, Tipo.dif_monto, Tipo.falta_suiche, Tipo.conciliado, Tipo.falta_ahorra);
        assertThat(r.get(1).diferenciaCts()).isEqualTo(100);
    }
}
