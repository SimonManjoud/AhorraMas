package ve.impulsatech.ahorramas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.soporte.ClienteApi;
import ve.impulsatech.ahorramas.soporte.PruebaIntegracion;
import ve.impulsatech.ahorramas.soporte.SistemasExternosFalsos.BcvFalso;

/** M1 + M2: registro, KYC, PIN/step-up, saldo rápido (F3) y vinculación multibanco por consentimiento (F1). */
class IdentidadYConsolidacionIntegracionTest extends PruebaIntegracion {

    @Test
    void registroDuplicadoYPiiCifradaEnReposo() {
        UsuarioPrueba u = registrar();
        var dup = cliente().post("/api/v1/auth/registro", Map.of("nacionalidadDoc", "V", "cedula", u.cedula(), "primerNombre", "Otra",
                "primerApellido", "Persona", "fechaNacimiento", "1990-01-01", "correo", "otra" + u.cedula() + "@x.com", "telefonoMovil", "04120000001",
                "pin", "123456", "dispositivo", Map.of("huella", "z")));
        assertThat(dup.status()).isEqualTo(409);
        String correoCifrado = jdbc.sql("SELECT correo_cifrado FROM identidad.usuario WHERE id_usuario = :u").param("u", u.idUsuario())
                .query(String.class).single();
        assertThat(correoCifrado).doesNotContain("@");   // R-06
        String pin = jdbc.sql("SELECT pin_hash FROM identidad.credencial WHERE id_usuario = :u").param("u", u.idUsuario()).query(String.class).single();
        assertThat(pin).startsWith("$2").doesNotContain("123456");
    }

    @Test
    void elRegistroExigeUnPinDeSeisDigitos() {
        for (String pin : List.of("1234", "12345", "1234567", "12345a")) {
            var r = cliente().post("/api/v1/auth/registro", Map.of("nacionalidadDoc", "V", "cedula", 29000000 + pin.length(),
                    "primerNombre", "Ana", "primerApellido", "Ruiz", "fechaNacimiento", "1990-01-01", "correo", "pin" + pin + "@x.com",
                    "telefonoMovil", "04120000002", "pin", pin, "dispositivo", Map.of("huella", "p" + pin)));
            assertThat(r.status()).as("PIN %s", pin).isEqualTo(400);
        }
    }

    @Test
    void sinKycNoSePuedeVincularBancos() {
        UsuarioPrueba u = registrar();
        var r = u.api().post("/api/v1/consentimientos", Map.of("instituciones", List.of(1), "permiteDebitoTdc", false));
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.texto("codigo")).isEqualTo("KYC_PENDIENTE");
    }

    @Test
    void tresPinesFallidosRevocanElDispositivoYAnulanLaSesion() {
        UsuarioPrueba u = registrar();
        Map<String, Object> malo = Map.of("nacionalidadDoc", "V", "cedula", u.cedula(), "pin", "9999", "huellaDispositivo", u.huella());
        var r1 = cliente().post("/api/v1/auth/login", malo);
        assertThat(r1.status()).isEqualTo(401);
        assertThat(r1.texto("codigo")).isEqualTo("PIN_INVALIDO");
        assertThat(r1.largo("detalles.intentosRestantes")).isEqualTo(2);
        cliente().post("/api/v1/auth/login", malo);
        var r3 = cliente().post("/api/v1/auth/login", malo);
        assertThat(r3.texto("codigo")).isEqualTo("DISPOSITIVO_REVOCADO");
        assertThat(u.api().get("/api/v1/me").status()).isEqualTo(401);   // la sesión previa quedó anulada
        var ok = cliente().post("/api/v1/auth/login", Map.of("nacionalidadDoc", "V", "cedula", u.cedula(), "pin", "123456",
                "huellaDispositivo", u.huella()));
        assertThat(ok.status()).isEqualTo(200);
    }

    @Test
    void saldoRapidoSoloLecturaLigadoAlDispositivo() {
        UsuarioPrueba u = usuarioConBancos();
        assertThat(u.api().post("/api/v1/auth/saldo-rapido", null).texto("codigo")).isEqualTo("STEP_UP_REQUERIDO");
        u.api().conStepUp(stepUp(u, false));
        var activ = u.api().post("/api/v1/auth/saldo-rapido", null);
        assertThat(activ.status()).isEqualTo(200);
        ClienteApi saldo = cliente().conToken(activ.texto("tokenSaldo"));

        assertThat(saldo.conCabecera("X-Dispositivo", u.huella()).get("/api/v1/saldo-rapido").largo("totalUsdCts")).isPositive();
        assertThat(saldo.conCabecera("X-Dispositivo", "otro-equipo").get("/api/v1/saldo-rapido").status()).isEqualTo(401);
        assertThat(saldo.get("/api/v1/consolidado").status()).isEqualTo(403);   // no sirve para ver detalle ni pagar

        u.api().conStepUp(null).post("/api/v1/dispositivos/actual/revocar", Map.of("motivo", "robo"));
        assertThat(saldo.conCabecera("X-Dispositivo", u.huella()).get("/api/v1/saldo-rapido").status()).isEqualTo(401);
    }

    @Test
    void consolidadoMultibancoEnBsYUsdALaTasaBcv() {
        UsuarioPrueba u = usuarioConBancos();
        var c = u.api().get("/api/v1/consolidado");
        assertThat(c.status()).isEqualTo(200);
        TasaBcv tasa = new TasaBcv(LocalDate.parse(c.texto("tasa.fecha")), c.largo("tasa.valorBsX10000"));
        assertThat(tasa.valorBsX10000()).isEqualTo(BcvFalso.valor(tasa.fecha()));
        // 2 cuentas en Bs (Bs 1.000.000 c/u) + $500 en divisas + 250 USDT de la plataforma cripto
        long esperadoUsd = 2 * tasa.bsAUsd(100_000_000) + 50_000 + 25_000;
        assertThat(c.largo("totalUsdCts")).isEqualTo(esperadoUsd);
        assertThat(c.largo("totalBsCts")).isEqualTo(200_000_000 + tasa.usdABs(75_000));
        List<Map<String, Object>> inst = (List<Map<String, Object>>) c.get("instituciones");
        assertThat(inst).hasSize(3);
        Map<String, Object> cripto = inst.stream().filter(i -> "plataforma_cripto".equals(i.get("tipo"))).findFirst().orElseThrow();
        assertThat(((List<Map<String, Object>>) cripto.get("cuentas")).getFirst().get("soloLectura")).isEqualTo(true);   // R-04

        // R-02: la BD solo guarda la referencia al token; el token está cifrado en la bóveda
        List<String> refs = jdbc.sql("SELECT ref_token_boveda FROM consolidacion.consentimiento WHERE id_usuario = :u AND estatus = 'activo'")
                .param("u", u.idUsuario()).query(String.class).list();
        assertThat(refs).hasSize(3).allMatch(r -> r.startsWith("vault:"));
        String cifrado = jdbc.sql("SELECT contenido_cifrado FROM boveda.secreto WHERE ref_secreto = :r").param("r", refs.getFirst())
                .query(String.class).single();
        assertThat(cifrado).doesNotContain("tok-");

        // Solo cuentas en Bs de bancos con débito pueden pagar TDC; la cripto nunca tiene débito (R-03)
        assertThat(u.api().get("/api/v1/pagos-tdc/cuentas-origen").lista()).hasSize(2);
        boolean debitoCripto = jdbc.sql("""
                SELECT c.permite_debito_tdc FROM consolidacion.consentimiento c JOIN consolidacion.institucion_financiera i USING (id_institucion)
                 WHERE c.id_usuario = :u AND i.tipo = 'plataforma_cripto'
                """).param("u", u.idUsuario()).query(Boolean.class).single();
        assertThat(debitoCripto).isFalse();
    }

    @Test
    void revocarUnConsentimientoSacaSusCuentasDelConsolidado() {
        UsuarioPrueba u = usuarioConBancos();
        var cons = u.api().get("/api/v1/consentimientos").lista();
        Map<String, Object> cripto = cons.stream().filter(c -> "CriptoAndes Exchange".equals(c.get("institucion"))).findFirst().orElseThrow();
        assertThat(u.api().delete("/api/v1/consentimientos/" + cripto.get("idConsentimiento")).status()).isEqualTo(204);
        var c = u.api().get("/api/v1/consolidado");
        assertThat((List<?>) c.get("instituciones")).hasSize(2);
        assertThat(u.api().get("/api/v1/consentimientos").lista())
                .filteredOn(x -> x.get("idConsentimiento").equals(cripto.get("idConsentimiento")))
                .extracting(x -> x.get("estatus")).containsExactly("revocado");
    }
}
