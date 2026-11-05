package ve.impulsatech.ahorramas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ve.impulsatech.ahorramas.soporte.ClienteApi;
import ve.impulsatech.ahorramas.soporte.PruebaIntegracion;

/** M4 (IA explicable, F4), M7 (backoffice/AML) y garantías de inmutabilidad (R-10, R-21, auditoría WORM). */
class CumplimientoEIaIntegracionTest extends PruebaIntegracion {

    private ClienteApi operador(String correo) {
        ClienteApi api = cliente();
        var r = api.post("/api/v1/backoffice/auth/login", Map.of("correoCorporativo", correo, "clave", "demo"));
        assertThat(r.status()).isEqualTo(200);
        return api.conToken(r.texto("accessToken"));
    }

    @Test
    void laIaRecomiendaExplicaYSoloActuaConAprobacion() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = u.api().post("/api/v1/metas", Map.of("nombre", "Fondo de emergencia", "categoria", "emergencia", "montoObjetivoUsdCts", 50_000,
                "fechaObjetivo", java.time.LocalDate.now().plusMonths(6).toString(), "frecuenciaCuota", "semanal")).largo("idMeta");

        var ev = u.api().post("/api/v1/perfil/evaluaciones", Map.of("objetivoPrincipal", "proteger", "horizonteMeses", 6, "toleranciaRiesgo", "baja"));
        assertThat(ev.status()).as("evaluación: %s", ev.cuerpo()).isEqualTo(200);
        assertThat(ev.texto("perfilResultante")).isEqualTo("conservador");
        List<Map<String, Object>> dist = (List<Map<String, Object>>) ev.get("recomendacion.distribucion");
        assertThat(dist.stream().mapToInt(d -> ((Number) d.get("porcentajePb")).intValue()).sum()).isEqualTo(10_000);   // R-23
        assertThat(ev.texto("recomendacion.explicacion")).contains("seudónimo");   // el LLM solo recibió el id seudónimo
        assertThat(ev.texto("recomendacion.disclaimer")).contains("no constituye asesoría");
        long idRec = ev.largo("recomendacion.idRecomendacion");

        // Nunca ejecuta sin aprobación con PIN
        assertThat(u.api().post("/api/v1/recomendaciones/" + idRec + "/decision", Map.of("aceptar", true, "idMeta", meta)).status()).isEqualTo(401);
        assertThat(u.api().get("/api/v1/metas/" + meta + "/reglas").lista()).isEmpty();

        u.api().conStepUp(stepUp(u, false));
        var dec = u.api().post("/api/v1/recomendaciones/" + idRec + "/decision", Map.of("aceptar", true, "entendioExplicacion", true, "idMeta", meta));
        assertThat(dec.texto("estatus")).isEqualTo("aceptada");
        assertThat(dec.texto("reglaCreada.tipo")).isEqualTo("pct_ingreso");
        Long origen = jdbc.sql("SELECT id_recomendacion FROM cofre.regla_ahorro WHERE id_meta = :m").param("m", meta).query(Long.class).single();
        assertThat(origen).isEqualTo(idRec);   // R-13: trazable a una recomendación aceptada
        assertThat(u.api().get("/api/v1/recomendaciones").lista()).hasSize(1);
    }

    @Test
    void kycEnListaDeSancionesGeneraAlertaAmlEscalableAUnRos() {
        UsuarioPrueba u = registrar();
        // fuerza una cédula terminada en 666 para el proveedor KYC
        jdbc.sql("UPDATE identidad.usuario SET cedula = :c WHERE id_usuario = :u").param("c", 40_000_666 + (int) (u.idUsuario() % 9) * 1000)
                .param("u", u.idUsuario()).update();
        String img = Base64.getEncoder().encodeToString("foto".getBytes());
        var kyc = u.api().post("/api/v1/kyc/verificaciones", Map.of("documentos",
                List.of(Map.of("tipo", "cedula", "contenidoBase64", img), Map.of("tipo", "selfie", "contenidoBase64", img))));
        assertThat(kyc.texto("resultado")).isEqualTo("rechazado");

        ClienteApi oficial = operador("cumplimiento@ahorramas.test");
        esperar(() -> assertThat(oficial.get("/api/v1/backoffice/alertas-aml").lista())
                .anyMatch(a -> ((Number) a.get("idUsuario")).longValue() == u.idUsuario() && "AML-04-LISTAS".equals(a.get("reglaDisparada"))));
        long idAlerta = oficial.get("/api/v1/backoffice/alertas-aml").lista().stream()
                .filter(a -> ((Number) a.get("idUsuario")).longValue() == u.idUsuario()).map(a -> ((Number) a.get("idAlerta")).longValue())
                .findFirst().orElseThrow();

        assertThat(oficial.patch("/api/v1/backoffice/alertas-aml/" + idAlerta, Map.of("estatus", "escalada")).status()).isEqualTo(400);
        assertThat(oficial.patch("/api/v1/backoffice/alertas-aml/" + idAlerta,
                Map.of("estatus", "escalada", "justificacion", "Coincidencia en listas")).texto("estatus")).isEqualTo("escalada");
        var ros = oficial.post("/api/v1/backoffice/reportes-regulatorios", Map.of("idAlerta", idAlerta));
        assertThat(ros.texto("estatus")).isEqualTo("borrador");
        assertThat(ros.texto("destinatario")).isEqualTo("UNIF");
        var enviado = oficial.post("/api/v1/backoffice/reportes-regulatorios/" + ros.largo("idReporteReg") + "/enviar", null);
        assertThat(enviado.texto("numeroAcuse")).startsWith("UNIF-ACK-");
    }

    @Test
    void controlDeAccesoDelBackoffice() {
        UsuarioPrueba u = registrar();
        assertThat(u.api().get("/api/v1/backoffice/casos").status()).isEqualTo(403);   // un usuario no es operador
        assertThat(cliente().post("/api/v1/backoffice/auth/login", Map.of("correoCorporativo", "soporte@ahorramas.test", "clave", "x")).status())
                .isEqualTo(401);
        ClienteApi soporte = operador("soporte@ahorramas.test");
        assertThat(soporte.get("/api/v1/backoffice/casos").status()).isEqualTo(200);
        assertThat(soporte.get("/api/v1/backoffice/alertas-aml").status()).isEqualTo(403);   // solo oficial de cumplimiento
        assertThat(soporte.get("/api/v1/metas").status()).isEqualTo(403);                    // un operador no es usuario final
    }

    @Test
    void ledgersYAuditoriaSonInmutables() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = u.api().post("/api/v1/metas", Map.of("nombre", "Laptop", "categoria", "estudio", "montoObjetivoUsdCts", 80_000,
                "fechaObjetivo", java.time.LocalDate.now().plusMonths(3).toString())).largo("idMeta");
        u.api().post("/api/v1/metas/" + meta + "/aportes", Map.of("montoUsdCts", 500));

        assertThatThrownBy(() -> jdbc.sql("UPDATE cofre.aporte SET monto_usd_cts = 1 WHERE id_meta = :m").param("m", meta).update())
                .hasMessageContaining("inmutable");                                                          // R-10
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM auditoria.evento").update()).hasMessageContaining("inmutable");
        assertThatThrownBy(() -> jdbc.sql("""
                WITH t AS (INSERT INTO puntos.transaccion_puntos (fecha_hora, concepto) VALUES (now(), 'meta') RETURNING id_transaccion)
                INSERT INTO puntos.asiento_puntos (id_transaccion, linea, id_cuenta_puntos, naturaleza, puntos)
                SELECT id_transaccion, 1, (SELECT id_cuenta_puntos FROM puntos.cuenta_puntos WHERE tipo = 'sistema'), 'debito', 10 FROM t
                """).update()).hasMessageContaining("R-21");                                                  // partida doble

        ClienteApi admin = operador("admin@ahorramas.test");
        var aud = admin.get("/api/v1/backoffice/auditoria?limite=20");
        assertThat(aud.get("cadenaIntegra")).isEqualTo(true);
        assertThat((List<?>) aud.get("eventos")).isNotEmpty();
    }

    @Test
    void notificacionesDeEventosDeDominio() {
        UsuarioPrueba u = usuarioConBancos();
        esperar(() -> assertThat(u.api().get("/api/v1/notificaciones").lista())
                .extracting(n -> n.get("titulo").toString()).contains("Identidad verificada", "Banco Ávila vinculado"));
        long id = ((Number) u.api().get("/api/v1/notificaciones").lista().getFirst().get("idNotificacion")).longValue();
        assertThat(u.api().post("/api/v1/notificaciones/" + id + "/leida", null).status()).isEqualTo(204);
    }
}
