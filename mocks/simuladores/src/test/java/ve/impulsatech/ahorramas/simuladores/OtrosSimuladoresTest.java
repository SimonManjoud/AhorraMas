package ve.impulsatech.ahorramas.simuladores;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** KYC, LLM (guardia de PII), notificaciones y consola. */
class OtrosSimuladoresTest extends PruebaBase {

    private static final String KYC = """
            {"nacionalidad":"V","cedula":%d,"primerNombre":"MARIA","primerApellido":"PEREZ",
             "fechaNacimiento":"1995-04-12",
             "documentos":[{"tipo":"cedula","hashSha256":"aa11"},{"tipo":"selfie","hashSha256":"bb22"}]}""";

    private static final String LLM_OK = """
            {"idSeudonimo": 834201, "perfil": "conservador",
             "rasgos": {"rangoIngreso": "R2", "volatilidadSaldoPb": 3400, "objetivoPrincipal": "proteger",
                        "horizonteMeses": 6, "toleranciaRiesgo": "baja", "metasActivas": 1},
             "distribucion": [{"instrumento": "USD", "porcentajePb": 6000}, {"instrumento": "Bs", "porcentajePb": 1000}]}""";

    @Test
    void kycAprobado() throws Exception {
        postJson("/kyc/verificaciones", KYC.formatted(12345678))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("aprobado"))
                .andExpect(jsonPath("$.pruebaVidaSuperada").value(true))
                .andExpect(jsonPath("$.coincideListaSanciones").value(false))
                .andExpect(jsonPath("$.refProveedor", org.hamcrest.Matchers.startsWith("KYC-")));
    }

    @Test
    void kycCedula666CoincideConSanciones() throws Exception {
        postJson("/kyc/verificaciones", KYC.formatted(12345666))
                .andExpect(jsonPath("$.resultado").value("rechazado"))
                .andExpect(jsonPath("$.coincideListaSanciones").value(true));
    }

    @Test
    void kycCedula000FallaPruebaDeVida() throws Exception {
        postJson("/kyc/verificaciones", KYC.formatted(12345000))
                .andExpect(jsonPath("$.resultado").value("rechazado"))
                .andExpect(jsonPath("$.pruebaVidaSuperada").value(false));
    }

    @Test
    void kycSinSelfieEsDocumentosIncompletos() throws Exception {
        postJson("/kyc/verificaciones", """
                {"nacionalidad":"V","cedula":12345678,"documentos":[{"tipo":"cedula","hashSha256":"aa"}]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DOCUMENTOS_INCOMPLETOS"));
    }

    @Test
    void llmExplicaConDatosSeudonimizados() throws Exception {
        postJson("/llm/v1/explicaciones", LLM_OK)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelo").value("sim-llm-1.0"))
                .andExpect(jsonPath("$.texto", containsString("conservador")))
                .andExpect(jsonPath("$.texto", containsString("60 % en USD")))
                .andExpect(jsonPath("$.texto", containsString("no una asesoría")));
    }

    @Test
    void llmRechazaPii() throws Exception {
        // clave prohibida
        postJson("/llm/v1/explicaciones", LLM_OK.replace("\"perfil\"", "\"cedula\": \"x\", \"perfil\""))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.codigo").value("PII_DETECTADA"));
        // secuencia de 7–9 dígitos fuera de idSeudonimo
        postJson("/llm/v1/explicaciones", LLM_OK.replace("\"R2\"", "\"V12345678\""))
                .andExpect(status().is(422));
        postJson("/llm/v1/explicaciones", LLM_OK.replace("\"horizonteMeses\": 6", "\"horizonteMeses\": 41412345"))
                .andExpect(status().is(422));
        // un @
        postJson("/llm/v1/explicaciones", LLM_OK.replace("\"R2\"", "\"maria@correo.com\""))
                .andExpect(status().is(422));
        // idSeudonimo largo sí está permitido
        postJson("/llm/v1/explicaciones", LLM_OK.replace("834201", "123456789"))
                .andExpect(status().isOk());
    }

    @Test
    void notificacionesSeGuardanYSeConsultanPorDestino() throws Exception {
        postJson("/notificaciones/sms", """
                {"destino":"+584141234567","titulo":"Ahorra+","mensaje":"Tu código es 482913","tipo":"otp"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estatus").value("enviada"))
                .andExpect(jsonPath("$.id").isString());
        mvc.perform(get("/notificaciones").param("destino", "+584141234567"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mensaje").value("Tu código es 482913"))
                .andExpect(jsonPath("$[0].canal").value("sms"));
        postJson("/notificaciones/fax", """
                {"destino":"x","mensaje":"y","tipo":"otp"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CANAL_INVALIDO"));
    }

    @Test
    void consolaMuestraTasaOtpYOperaciones() throws Exception {
        postJson("/notificaciones/push", """
                {"destino":"disp-1","titulo":"OTP","mensaje":"Código 777111","tipo":"otp"}""");
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Consola de simuladores")))
                .andExpect(content().string(containsString("Tasa BCV del día")))
                .andExpect(content().string(containsString("Código 777111")))
                .andExpect(content().string(containsString("Operaciones del suiche")))
                .andExpect(content().string(containsString("setInterval(refrescar, 5000)")));
    }

    @Test
    void rutaInexistenteDevuelveErrorComun() throws Exception {
        mvc.perform(get("/no/existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_EXISTE"));
    }
}
