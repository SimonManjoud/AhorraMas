package ve.impulsatech.ahorramas.soporte;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.awaitility.Awaitility;
import org.awaitility.core.ThrowingRunnable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las pruebas de integración: aplicación completa sobre PostgreSQL embebido real (Flyway,
 * triggers, restricciones), HTTP real y sistemas externos sustituidos por dobles en proceso.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(SistemasExternosFalsos.class)
public abstract class PruebaIntegracion {

    @Autowired
    protected Environment env;
    @Autowired
    protected JsonMapper json;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected SistemasExternosFalsos.SuicheFalso suiche;
    @Autowired
    protected SistemasExternosFalsos.OpenBankingFalso openBanking;

    protected ClienteApi cliente() {
        return new ClienteApi("http://localhost:" + env.getProperty("local.server.port"), json);
    }

    /** Usuario autenticado de prueba con datos únicos. */
    public record UsuarioPrueba(long idUsuario, int cedula, String pin, String huella, ClienteApi api) {
    }

    protected UsuarioPrueba registrar() {
        int cedula = ThreadLocalRandom.current().nextInt(10_000_000, 29_999_999);
        String tel = "0414" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
        String huella = "disp-" + cedula;
        ClienteApi api = cliente();
        var r = api.post("/api/v1/auth/registro", Map.of("nacionalidadDoc", "V", "cedula", cedula, "primerNombre", "María",
                "primerApellido", "Pérez", "fechaNacimiento", "1995-04-12", "correo", "u" + cedula + "@correo.com", "telefonoMovil", tel,
                "pin", "1234", "dispositivo", Map.of("huella", huella, "plataforma", "Android", "modelo", "Pixel 8")));
        assertThat(r.status()).as("registro: %s", r.cuerpo()).isEqualTo(201);
        api.conToken(r.texto("accessToken"));
        return new UsuarioPrueba(r.largo("usuario.idUsuario"), cedula, "1234", huella, api);
    }

    protected void verificarKyc(UsuarioPrueba u) {
        String cedula = Base64.getEncoder().encodeToString(("foto-cedula-" + u.cedula()).getBytes());
        String selfie = Base64.getEncoder().encodeToString(("foto-selfie-" + u.cedula()).getBytes());
        var r = u.api().post("/api/v1/kyc/verificaciones", Map.of("documentos",
                List.of(Map.of("tipo", "cedula", "contenidoBase64", cedula), Map.of("tipo", "selfie", "contenidoBase64", selfie))));
        assertThat(r.status()).as("kyc: %s", r.cuerpo()).isEqualTo(200);
        assertThat(r.texto("resultado")).isEqualTo("aprobado");
    }

    /** F1 completo: selección múltiple → consentimientos → "autorización en el banco" → callback. */
    protected void vincularBancos(UsuarioPrueba u, String... codigos) {
        var inst = u.api().get("/api/v1/instituciones").lista();
        List<Long> ids = inst.stream().filter(i -> List.of(codigos).contains(i.get("codigo")))
                .map(i -> ((Number) i.get("idInstitucion")).longValue()).toList();
        var r = u.api().post("/api/v1/consentimientos", Map.of("instituciones", ids, "permiteDebitoTdc", true));
        assertThat(r.status()).as("consentimientos: %s", r.cuerpo()).isEqualTo(201);
        for (Map<String, Object> s : r.lista()) {
            String url = (String) s.get("urlAutorizacion");
            String estado = url.substring(url.indexOf("state=") + 6, url.indexOf('&', url.indexOf("state=")));
            var cb = cliente().get("/api/v1/consentimientos/callback?state=" + estado + "&code=codigo-" + estado);
            assertThat(cb.status()).isEqualTo(200);
            assertThat(cb.cuerpo().toString()).contains("Listo");
        }
    }

    protected UsuarioPrueba usuarioConBancos() {
        UsuarioPrueba u = registrar();
        verificarKyc(u);
        vincularBancos(u, "0901", "0902", "C001");
        return u;
    }

    protected String stepUp(UsuarioPrueba u, boolean conOtp) {
        Map<String, Object> cuerpo = new java.util.HashMap<>(Map.of("pin", u.pin()));
        if (conOtp) {
            var otp = u.api().post("/api/v1/auth/otp", Map.of("proposito", "pago"));
            cuerpo.put("idDesafio", otp.texto("idDesafio"));
            cuerpo.put("otp", otp.texto("otpDemo"));
        }
        var r = u.api().post("/api/v1/auth/step-up", cuerpo);
        assertThat(r.status()).as("step-up: %s", r.cuerpo()).isEqualTo(200);
        return r.texto("stepUpToken");
    }

    protected void esperar(ThrowingRunnable aserto) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(200)).untilAsserted(aserto);
    }
}
