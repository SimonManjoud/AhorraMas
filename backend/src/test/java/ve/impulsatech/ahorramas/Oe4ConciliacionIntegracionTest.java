package ve.impulsatech.ahorramas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;
import ve.impulsatech.ahorramas.soporte.ClienteApi;
import ve.impulsatech.ahorramas.soporte.PruebaIntegracion;

/**
 * Criterio de éxito del OE4: 500 transacciones de prueba —incluyendo timeouts, rechazos, capturas
 * rechazadas, diferencias de monto y faltantes en el archivo del suiche— con 100 % de conciliación
 * y cero duplicados, aunque la app reintente con la misma Idempotency-Key.
 */
class Oe4ConciliacionIntegracionTest extends PruebaIntegracion {

    static final int TRANSACCIONES = 500;

    @Autowired
    UsuariosApi usuarios;

    @Test
    void quinientasTransaccionesConciliadasSinDuplicados() {
        UsuarioPrueba u = usuarioConBancos();
        usuarios.cambiarLimite(u.idUsuario(), 1_000_000_000_000L, 10_000, "Prueba OE4", 1);
        long idTarjeta = ((Number) u.api().get("/api/v1/tarjetas").lista().getFirst().get("idTarjeta")).longValue();
        long idCuenta = ((Number) u.api().get("/api/v1/pagos-tdc/cuentas-origen").lista().getFirst().get("idCuenta")).longValue();
        u.api().conStepUp(stepUp(u, true));

        int[] sufijos = {0, 25, 40, 60, 80, 13, 51, 77, 99, 98};   // feliz ×5, timeout, rechazo, captura rechazada, dif. monto, falta en suiche
        Map<String, Long> idPorClave = new HashMap<>();
        List<String> claves = new ArrayList<>();
        int reintentos = 0;
        for (int i = 0; i < TRANSACCIONES; i++) {
            if (i > 0 && i % 100 == 0) {
                u.api().conStepUp(stepUp(u, true));   // el step-up dura 5 minutos
            }
            String clave = UUID.randomUUID().toString();
            long monto = (1_000 + i) * 100L + sufijos[i % sufijos.length];
            var r = pagar(u.api(), clave, idTarjeta, idCuenta, monto);
            assertThat(r.status()).as("pago %d: %s", i, r.cuerpo()).isEqualTo(201);
            idPorClave.put(clave, r.largo("idPago"));
            claves.add(clave);
            if (i % 7 == 0) {   // la app reintenta (doble toque / red caída) con la misma clave
                var repetido = pagar(u.api(), clave, idTarjeta, idCuenta, monto);
                assertThat(repetido.status()).isEqualTo(200);
                assertThat(repetido.largo("idPago")).isEqualTo(r.largo("idPago"));
                reintentos++;
            }
        }
        assertThat(reintentos).isGreaterThan(70);

        // Cero duplicados: un pago por clave y a lo sumo una captura por referencia en el suiche
        long pagos = jdbc.sql("SELECT COUNT(*) FROM pagos.pago_tdc WHERE id_usuario = :u").param("u", u.idUsuario()).query(Long.class).single();
        assertThat(pagos).isEqualTo(TRANSACCIONES);
        assertThat(claves).allSatisfy(k -> assertThat(suiche.capturasPorReferencia.getOrDefault(k, new java.util.concurrent.atomic.AtomicInteger()).get())
                .isLessThanOrEqualTo(1));

        // Conciliación del día desde el backoffice (rol conciliación)
        ClienteApi operador = cliente();
        var login = operador.post("/api/v1/backoffice/auth/login", Map.of("correoCorporativo", "conciliacion@ahorramas.test", "clave", "demo"));
        assertThat(login.status()).isEqualTo(200);
        operador.conToken(login.texto("accessToken"));
        var conc = operador.post("/api/v1/backoffice/conciliaciones", Map.of("fecha", LocalDate.now(ZoneId.of("America/Caracas")).toString()));
        assertThat(conc.status()).as("conciliación: %s", conc.cuerpo()).isEqualTo(200);

        // Ningún pago queda en un estado ambiguo
        Map<String, Long> porEstado = new HashMap<>();
        jdbc.sql("SELECT estado_actual, COUNT(*) FROM pagos.pago_tdc WHERE id_usuario = :u GROUP BY estado_actual").param("u", u.idUsuario())
                .query((rs, n) -> porEstado.put(rs.getString(1), rs.getLong(2))).list();
        assertThat(porEstado).doesNotContainKeys("PENDIENTE", "AUTORIZADO", "EN_VERIFICACION", "CAPTURADO", "CON_DIFERENCIA");
        assertThat(porEstado.get("CONCILIADO")).isEqualTo(300);   // 5 felices + timeout resuelto, por cada 10
        assertThat(porEstado.get("RECHAZADO")).isEqualTo(100);    // rechazo + captura rechazada
        assertThat(porEstado.get("REVERSADO")).isEqualTo(100);    // diferencias detectadas → reverso

        // 100 % conciliado (R-19): todo pago que llegó al suiche tiene su resultado de conciliación
        long sinResultado = jdbc.sql("""
                SELECT COUNT(*) FROM pagos.pago_tdc p
                 WHERE p.id_usuario = :u AND p.referencia_suiche IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM pagos.resultado_conciliacion r WHERE r.id_pago = p.id_pago)
                """).param("u", u.idUsuario()).query(Long.class).single();
        assertThat(sinResultado).isZero();
        long difMonto = jdbc.sql("""
                SELECT COUNT(*) FROM pagos.resultado_conciliacion r JOIN pagos.pago_tdc p USING (id_pago)
                 WHERE p.id_usuario = :u AND r.resultado = 'dif_monto'
                """).param("u", u.idUsuario()).query(Long.class).single();
        long faltaSuiche = jdbc.sql("""
                SELECT COUNT(*) FROM pagos.resultado_conciliacion r JOIN pagos.pago_tdc p USING (id_pago)
                 WHERE p.id_usuario = :u AND r.resultado = 'falta_suiche'
                """).param("u", u.idUsuario()).query(Long.class).single();
        assertThat(difMonto).isEqualTo(50);
        assertThat(faltaSuiche).isEqualTo(50);

        // Cada diferencia abre un caso en el backoffice
        esperar(() -> assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM backoffice.caso_soporte c JOIN pagos.pago_tdc p USING (id_pago) WHERE p.id_usuario = :u AND c.tipo = 'diferencia'
                """).param("u", u.idUsuario()).query(Long.class).single()).isEqualTo(100));

        // Volver a conciliar el mismo día es idempotente
        var otraVez = operador.post("/api/v1/backoffice/conciliaciones", Map.of("fecha", LocalDate.now(ZoneId.of("America/Caracas")).toString()));
        assertThat(otraVez.largo("conciliados") + otraVez.largo("diferencias")).isEqualTo(conc.largo("conciliados") + conc.largo("diferencias"));
    }

    private static ClienteApi.Respuesta pagar(ClienteApi api, String clave, long idTarjeta, long idCuenta, long monto) {
        return api.conCabecera("Idempotency-Key", clave).post("/api/v1/pagos-tdc",
                Map.of("idTarjeta", idTarjeta, "idCuentaOrigen", idCuenta, "montoCts", monto, "beneficiarioConfirmado", true));
    }
}
