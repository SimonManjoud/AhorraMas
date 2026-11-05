package ve.impulsatech.ahorramas.simuladores.instituciones;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import ve.impulsatech.ahorramas.simuladores.PruebaBase;

class OpenBankingTest extends PruebaBase {

    private static final String REDIRECT = "ahorramas://oauth/callback";
    private static final String HUELLA = "sha256:prueba";

    @Test
    void catalogoDeInstituciones() throws Exception {
        mvc.perform(get("/ob/instituciones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].codigo").value("0901"))
                .andExpect(jsonPath("$[0].nombre").value("Banco Ávila"))
                .andExpect(jsonPath("$[4].tipo").value("plataforma_cripto"))
                .andExpect(jsonPath("$[4].tipoPlataforma").value("exchange"));
    }

    @Test
    void paginaDeAutorizacionEsHtmlDelBanco() throws Exception {
        mvc.perform(get("/ob/0901/oauth/authorize")
                        .param("client_id", "ahorra-mas").param("redirect_uri", REDIRECT).param("state", "xyz")
                        .param("scope", "saldos:leer movimientos:leer").param("login_hint", "V12345678"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Banco Ávila")))
                .andExpect(content().string(containsString("SIMULADOR")))
                .andExpect(content().string(containsString("Consultar el saldo")))
                .andExpect(content().string(containsString("V12345678")))
                .andExpect(content().string(containsString("Autorizar")))
                .andExpect(content().string(containsString("Rechazar")));
    }

    @Test
    void flujoCompletoConCodigoDeUnSoloUso() throws Exception {
        String code = autorizar("0901", "V12345678", "saldos:leer movimientos:leer");

        // sin certificado de cliente (mTLS simulado) ⇒ 401
        mvc.perform(post("/ob/0901/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code").param("code", code)
                        .param("redirect_uri", REDIRECT).param("client_id", "ahorra-mas"))
                .andExpect(status().isUnauthorized());

        MvcResult r = canjear("0901", code).andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(7776000))
                .andExpect(jsonPath("$.scope").value("saldos:leer movimientos:leer"))
                .andReturn();
        String token = leer(r, "$.access_token");

        // el código ya se usó
        canjear("0901", code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));

        MvcResult cuentas = mvc.perform(get("/ob/0901/cuentas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].moneda").value("VES"))
                .andExpect(jsonPath("$[0].refCuenta", org.hamcrest.Matchers.startsWith("0901-")))
                .andReturn();
        String refCuenta = leer(cuentas, "$[0].refCuenta");

        mvc.perform(get("/ob/0901/cuentas/" + refCuenta + "/saldo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refCuenta").value(refCuenta))
                .andExpect(jsonPath("$.saldoCts").isNumber());

        MvcResult movs = mvc.perform(get("/ob/0901/cuentas/" + refCuenta + "/movimientos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        List<String> descripciones = leer(movs, "$[*].descripcion");
        List<String> fechas = leer(movs, "$[*].fechaHora");
        assertThat(descripciones).contains("PAGO NOMINA");
        assertThat(fechas).isSortedAccordingTo(String::compareTo);

        // datos deterministas: misma cédula ⇒ mismas cuentas
        String code2 = autorizar("0901", "V12345678", "saldos:leer");
        String token2 = leer(canjear("0901", code2).andReturn(), "$.access_token");
        mvc.perform(get("/ob/0901/cuentas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token2))
                .andExpect(jsonPath("$[0].refCuenta").value(refCuenta));

        // revocado ⇒ 401 TOKEN_INVALIDO
        mvc.perform(post("/ob/0901/oauth/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().json("{}"));
        mvc.perform(get("/ob/0901/cuentas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("TOKEN_INVALIDO"));
    }

    @Test
    void rechazarRedirigeConAccessDenied() throws Exception {
        MvcResult r = mvc.perform(post("/ob/0902/oauth/authorize").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("redirect_uri", REDIRECT).param("state", "abc").param("scope", "saldos:leer")
                        .param("accion", "rechazar"))
                .andExpect(status().isFound())
                .andReturn();
        String location = r.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(REDIRECT).contains("error=access_denied").contains("state=abc");
    }

    @Test
    void criptoRechazaDebitoTdc() throws Exception {
        mvc.perform(get("/ob/C001/oauth/authorize").param("client_id", "ahorra-mas").param("redirect_uri", REDIRECT)
                        .param("state", "s").param("scope", "saldos:leer debito:tdc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("ALCANCE_NO_PERMITIDO"));
        // un banco sí lo permite
        mvc.perform(get("/ob/0902/oauth/authorize").param("client_id", "ahorra-mas").param("redirect_uri", REDIRECT)
                        .param("state", "s").param("scope", "saldos:leer debito:tdc"))
                .andExpect(status().isOk());
    }

    @Test
    void criptoTieneBilleteraUsdtYSinTarjetas() throws Exception {
        String token = leer(canjear("C002", autorizar("C002", "V11223344", "saldos:leer movimientos:leer"))
                .andReturn(), "$.access_token");
        mvc.perform(get("/ob/C002/cuentas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tipo").value("billetera"))
                .andExpect(jsonPath("$[0].moneda").value("USDT"));
        mvc.perform(get("/ob/C002/tarjetas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(content().json("[]"));
    }

    @Test
    void bancoOrinocoEmiteTdcYElTokenEsPorInstitucion() throws Exception {
        String token = leer(canjear("0902", autorizar("0902", "V12345678", "saldos:leer debito:tdc"))
                .andReturn(), "$.access_token");
        mvc.perform(get("/ob/0902/tarjetas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].refTarjeta", org.hamcrest.Matchers.startsWith("TDC-0902-")))
                .andExpect(jsonPath("$[0].estadoCuenta.saldoTotalCts").isNumber())
                .andExpect(jsonPath("$[0].estadoCuenta.pagoMinimoCts").isNumber());
        // un token de 0902 no sirve en 0901
        mvc.perform(get("/ob/0901/cuentas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inyectarMovimientoAjustaSaldo() throws Exception {
        String token = leer(canjear("0903", autorizar("0903", "V20304050", "saldos:leer movimientos:leer"))
                .andReturn(), "$.access_token");
        String ref = leer(mvc.perform(get("/ob/0903/cuentas").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn(), "$[0].refCuenta");
        long antes = ((Number) leer(mvc.perform(get("/ob/0903/cuentas/" + ref + "/saldo")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andReturn(), "$.saldoCts")).longValue();

        postJson("/simulador/ob/0903/movimientos", """
                {"cedula":"V20304050","refCuenta":"%s","naturaleza":"credito","montoCts":500000,
                 "descripcion":"PAGO NOMINA","clasificacion":"ingreso"}""".formatted(ref))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clasificacionSugerida").value("ingreso"))
                .andExpect(jsonPath("$.montoCts").value(500000));

        mvc.perform(get("/ob/0903/cuentas/" + ref + "/saldo").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.saldoCts").value(antes + 500000));
    }

    private String autorizar(String codigo, String cedula, String scope) throws Exception {
        MvcResult r = mvc.perform(post("/ob/" + codigo + "/oauth/authorize")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "ahorra-mas").param("redirect_uri", REDIRECT).param("state", "xyz")
                        .param("scope", scope).param("cedula", cedula)
                        .param("usuario", "cualquiera").param("clave", "1234").param("accion", "autorizar"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("state=xyz")))
                .andReturn();
        String location = r.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location).startsWith(REDIRECT);
        return UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("code");
    }

    private org.springframework.test.web.servlet.ResultActions canjear(String codigo, String code) throws Exception {
        return mvc.perform(post("/ob/" + codigo + "/oauth/token").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("X-Client-Cert-Fingerprint", HUELLA)
                .param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", REDIRECT).param("client_id", "ahorra-mas"));
    }
}
