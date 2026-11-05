package ve.impulsatech.ahorramas.simuladores.instituciones;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Token;

/** OAuth2 authorization code simulado por institución (contrato §2.1). */
@RestController
public class OAuthController {

    private final OAuthService oauth;

    public OAuthController(OAuthService oauth) {
        this.oauth = oauth;
    }

    /** Página HTML de la banca en línea simulada. */
    @GetMapping("/ob/{codigo}/oauth/authorize")
    public ResponseEntity<String> paginaAutorizacion(@PathVariable String codigo,
                                                     @RequestParam(name = "client_id", required = false) String clientId,
                                                     @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                                     @RequestParam(required = false) String state,
                                                     @RequestParam(required = false) String scope,
                                                     @RequestParam(name = "login_hint", required = false) String loginHint) {
        Institucion inst = Institucion.obtener(codigo);
        exigirRedirect(redirectUri);
        List<String> alcances = oauth.validarAlcances(inst, scope);
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(PaginaAutorizacion.html(inst, clientId, redirectUri, state, alcances, loginHint));
    }

    /** Envío del formulario: Autorizar → {@code ?code&state}; Rechazar → {@code ?error=access_denied&state}. */
    @PostMapping(value = "/ob/{codigo}/oauth/authorize", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Void> decidir(@PathVariable String codigo,
                                        @RequestParam(name = "redirect_uri", required = false) String redirectUri,
                                        @RequestParam(required = false) String state,
                                        @RequestParam(required = false) String scope,
                                        @RequestParam(required = false) String cedula,
                                        @RequestParam(defaultValue = "autorizar") String accion) {
        Institucion inst = Institucion.obtener(codigo);
        exigirRedirect(redirectUri);
        UriComponentsBuilder destino = UriComponentsBuilder.fromUriString(redirectUri);
        if ("rechazar".equalsIgnoreCase(accion)) {
            destino.queryParam("error", "access_denied");
        } else {
            List<String> alcances = oauth.validarAlcances(inst, scope);
            String ced = BancoService.normalizarCedula(cedula);
            destino.queryParam("code", oauth.emitirCodigo(inst, ced, alcances, redirectUri));
        }
        if (state != null && !state.isEmpty()) {
            destino.queryParam("state", state);
        }
        URI location = destino.encode().build().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    @PostMapping(value = "/ob/{codigo}/oauth/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Token token(@PathVariable String codigo,
                       @RequestHeader(name = "X-Client-Cert-Fingerprint", required = false) String huella,
                       @RequestParam(name = "grant_type", required = false) String grantType,
                       @RequestParam(required = false) String code,
                       @RequestParam(name = "redirect_uri", required = false) String redirectUri) {
        Institucion inst = Institucion.obtener(codigo);
        if (huella == null || huella.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "CERTIFICADO_REQUERIDO",
                    "Falta el certificado de cliente (header X-Client-Cert-Fingerprint, simula mTLS)");
        }
        return oauth.canjear(inst, grantType, code, redirectUri);
    }

    @PostMapping(value = "/ob/{codigo}/oauth/revoke", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> revocar(@PathVariable String codigo, @RequestParam(required = false) String token) {
        Institucion.obtener(codigo);
        oauth.revocar(token);
        return Map.of();
    }

    private static void exigirRedirect(String redirectUri) {
        if (redirectUri == null || redirectUri.isBlank()) {
            throw ApiException.solicitudInvalida("Falta 'redirect_uri'");
        }
    }
}
