package ve.impulsatech.ahorramas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import ve.impulsatech.ahorramas.soporte.ClienteApi.Respuesta;
import ve.impulsatech.ahorramas.soporte.PruebaIntegracion;

/** M3 · F2: pago de TDC con autorización, captura, reverso, idempotencia, step-up y límites. */
class PagosTdcIntegracionTest extends PruebaIntegracion {

    record Contexto(UsuarioPrueba u, long idTarjeta, long idCuenta) {
    }

    private Contexto preparar() {
        UsuarioPrueba u = usuarioConBancos();
        var tarjetas = u.api().get("/api/v1/tarjetas").lista();
        assertThat(tarjetas).hasSize(1);
        assertThat(tarjetas.getFirst().get("ultimos4")).isEqualTo("7781");
        assertThat(tarjetas.getFirst().get("estadoCuenta")).isNotNull();
        long idTarjeta = ((Number) tarjetas.getFirst().get("idTarjeta")).longValue();
        var benef = u.api().post("/api/v1/tarjetas/" + idTarjeta + "/beneficiario", null);
        assertThat(benef.texto("titular")).isEqualTo("MARIA PEREZ");
        long idCuenta = ((Number) u.api().get("/api/v1/pagos-tdc/cuentas-origen").lista().getFirst().get("idCuenta")).longValue();
        u.api().conStepUp(stepUp(u, true));
        return new Contexto(u, idTarjeta, idCuenta);
    }

    private Respuesta pagar(Contexto c, long monto, String clave) {
        return c.u().api().conCabecera("Idempotency-Key", clave)
                .post("/api/v1/pagos-tdc", Map.of("idTarjeta", c.idTarjeta(), "idCuentaOrigen", c.idCuenta(), "montoCts", monto,
                        "beneficiarioConfirmado", true));
    }

    @Test
    void caminoFelizIdempotenteYPuntual() {
        Contexto c = preparar();
        var cot = c.u().api().get("/api/v1/pagos-tdc/cotizacion?montoCts=1500000");
        assertThat(cot.largo("comisionCts")).isEqualTo(5_000);

        String clave = UUID.randomUUID().toString();
        var r1 = pagar(c, 1_500_000, clave);
        assertThat(r1.status()).as("pago: %s", r1.cuerpo()).isEqualTo(201);
        assertThat(r1.texto("estado")).isEqualTo("CAPTURADO");
        assertThat(r1.get("esPuntual")).isEqualTo(true);
        assertThat((List<?>) r1.get("historial")).hasSize(3);   // PENDIENTE → AUTORIZADO → CAPTURADO

        var r2 = pagar(c, 1_500_000, clave);                    // el usuario tocó dos veces / reintento de red
        assertThat(r2.status()).isEqualTo(200);
        assertThat(r2.largo("idPago")).isEqualTo(r1.largo("idPago"));
        long pagos = jdbc.sql("SELECT COUNT(*) FROM pagos.pago_tdc WHERE clave_idempotencia = :k").param("k", clave).query(Long.class).single();
        assertThat(pagos).isEqualTo(1);
        assertThat(suiche.capturasPorReferencia.get(clave).get()).isEqualTo(1);   // cero cobros dobles

        // R-18: pago puntual → puntos financiados por el banco aliado
        esperar(() -> assertThat(c.u().api().get("/api/v1/puntos/movimientos").lista())
                .anyMatch(m -> "pago_puntual".equals(m.get("concepto")) && ((Number) m.get("puntos")).longValue() == 200));
    }

    @Test
    void exigeIdempotencyKeyYStepUpConOtp() {
        Contexto c = preparar();
        var sinClave = c.u().api().conCabecera("Idempotency-Key", null).post("/api/v1/pagos-tdc",
                Map.of("idTarjeta", c.idTarjeta(), "idCuentaOrigen", c.idCuenta(), "montoCts", 1000, "beneficiarioConfirmado", true));
        assertThat(sinClave.texto("codigo")).isEqualTo("IDEMPOTENCY_KEY_REQUERIDA");

        c.u().api().conStepUp(stepUp(c.u(), false));   // solo PIN: insuficiente para pagos
        var soloPin = pagar(c, 1000, UUID.randomUUID().toString());
        assertThat(soloPin.status()).isEqualTo(401);
        assertThat(soloPin.texto("detalles.nivelRequerido")).isEqualTo("pin_otp");

        c.u().api().conStepUp(stepUp(c.u(), true));
        var sinBenef = c.u().api().conCabecera("Idempotency-Key", UUID.randomUUID().toString()).post("/api/v1/pagos-tdc",
                Map.of("idTarjeta", c.idTarjeta(), "idCuentaOrigen", c.idCuenta(), "montoCts", 1000, "beneficiarioConfirmado", false));
        assertThat(sinBenef.texto("codigo")).isEqualTo("R-16");
    }

    @Test
    void rechazoTimeoutYCapturaRechazada() {
        Contexto c = preparar();
        var rechazo = pagar(c, 1_000_051, UUID.randomUUID().toString());
        assertThat(rechazo.texto("estado")).isEqualTo("RECHAZADO");
        assertThat(rechazo.largo("comisionCts")).isZero();                       // R-17
        assertThat(rechazo.texto("motivoRechazo")).isEqualTo("Fondos insuficientes");

        var timeout = pagar(c, 1_000_013, UUID.randomUUID().toString());          // respuesta perdida → se consulta, nunca se reenvía a ciegas
        assertThat(timeout.texto("estado")).isEqualTo("CAPTURADO");
        assertThat((List<Map<String, Object>>) timeout.get("historial")).extracting(h -> h.get("estadoNuevo"))
                .containsExactly("PENDIENTE", "EN_VERIFICACION", "AUTORIZADO", "CAPTURADO");

        String clave77 = UUID.randomUUID().toString();
        var captura = pagar(c, 1_000_077, clave77);
        assertThat(captura.texto("estado")).isEqualTo("RECHAZADO");
        assertThat(suiche.ops.get(clave77).estado()).isEqualTo("reversado");      // se reversó la autorización
    }

    @Test
    void limiteDiarioDeOperaciones() {
        Contexto c = preparar();
        for (int i = 0; i < 5; i++) {
            assertThat(pagar(c, 10_000 + i, UUID.randomUUID().toString()).status()).isEqualTo(201);
        }
        var sexto = pagar(c, 10_000, UUID.randomUUID().toString());
        assertThat(sexto.status()).isEqualTo(422);
        assertThat(sexto.texto("codigo")).isEqualTo("LIMITE_EXCEDIDO");
        assertThat(c.u().api().get("/api/v1/limites").largo("operacionesHoy")).isEqualTo(5);
    }

    @Test
    void elHistorialDeEstadosEsInmutable() {
        Contexto c = preparar();
        long idPago = pagar(c, 20_000, UUID.randomUUID().toString()).largo("idPago");
        assertThatThrownBy(() -> jdbc.sql("UPDATE pagos.historial_estado_pago SET actor = 'operador' WHERE id_pago = :p").param("p", idPago).update())
                .hasMessageContaining("inmutable");
    }
}
