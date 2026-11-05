package ve.impulsatech.ahorramas.consolidacion.adaptador.salida.externos;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.integracion.PropiedadesIntegraciones;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.*;

/**
 * Adaptador Open Banking (capa anticorrupción). Traduce el contrato del banco al modelo de Ahorra+.
 * En producción habría uno por banco con mTLS real; en el MVP todos hablan con el simulador.
 */
@Component
class OpenBankingHttp implements OpenBanking {

    private final RestClient http;
    private final PropiedadesIntegraciones props;

    OpenBankingHttp(@Qualifier("simuladores") RestClient http, PropiedadesIntegraciones props) {
        this.http = http;
        this.props = props;
    }

    private String redirectUri() {
        return props.urlPublicaNucleo() + "/api/v1/consentimientos/callback";
    }

    @Override
    public String urlAutorizacion(String codigo, String estado, List<String> alcances, String loginHint) {
        String base = props.urlPublicaSimuladores() == null ? props.urlSimuladores() : props.urlPublicaSimuladores();
        return UriComponentsBuilder.fromUriString(base).path("/ob/{codigo}/oauth/authorize")
                .queryParam("client_id", props.clientId())
                .queryParam("redirect_uri", redirectUri())
                .queryParam("state", estado)
                .queryParam("scope", String.join(" ", alcances))
                .queryParam("login_hint", loginHint)
                .encode().buildAndExpand(codigo).toUriString();
    }

    @Override
    public String canjearCodigo(String codigo, String code) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri());
        form.add("client_id", props.clientId());
        try {
            Map<String, Object> r = http.post().uri("/ob/{c}/oauth/token", codigo).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(new ParameterizedTypeReference<>() { });
            if (r == null || r.get("access_token") == null) {
                throw ErrorNegocio.servicioExterno("El banco no entregó el token de acceso");
            }
            return (String) r.get("access_token");
        } catch (RestClientException e) {
            throw ErrorNegocio.servicioExterno("No se pudo completar la autorización con el banco");
        }
    }

    @Override
    public List<CuentaExterna> cuentas(String codigo, String token) {
        return llamar(() -> http.get().uri("/ob/{c}/cuentas", codigo).headers(h -> h.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<List<CuentaExterna>>() { }));
    }

    @Override
    public SaldoExterno saldo(String codigo, String token, String refCuenta) {
        return llamar(() -> http.get().uri("/ob/{c}/cuentas/{r}/saldo", codigo, refCuenta).headers(h -> h.setBearerAuth(token)).retrieve()
                .body(SaldoExterno.class));
    }

    @Override
    public List<MovimientoExterno> movimientos(String codigo, String token, String refCuenta, LocalDate desde) {
        List<MovimientoExterno> r = llamar(() -> http.get().uri("/ob/{c}/cuentas/{r}/movimientos?desde={d}", codigo, refCuenta, desde)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(new ParameterizedTypeReference<List<MovimientoExterno>>() { }));
        return r == null ? List.of() : r;
    }

    @Override
    public void revocar(String codigo, String token) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("token", token);
        http.post().uri("/ob/{c}/oauth/revoke", codigo).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
    }

    private static <T> T llamar(java.util.function.Supplier<T> s) {
        try {
            return s.get();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new ErrorNegocio(ErrorNegocio.Tipo.SERVICIO_EXTERNO, "TOKEN_RECHAZADO", "El banco rechazó el token del consentimiento", Map.of());
            }
            throw ErrorNegocio.servicioExterno("Error consultando la institución: " + e.getStatusCode());
        } catch (RestClientException e) {
            throw ErrorNegocio.servicioExterno("La institución no está disponible en este momento");
        }
    }
}
