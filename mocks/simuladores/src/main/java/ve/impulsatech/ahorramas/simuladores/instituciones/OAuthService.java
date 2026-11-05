package ve.impulsatech.ahorramas.simuladores.instituciones;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Token;

/**
 * Servidor de autorización OAuth2 (authorization code) simulado, uno por institución.
 * Los códigos son de un solo uso y expiran a los 10 minutos; los tokens duran 90 días.
 */
@Service
public class OAuthService {

    public static final String SALDOS = "saldos:leer";
    public static final String MOVIMIENTOS = "movimientos:leer";
    public static final String DEBITO_TDC = "debito:tdc";
    static final List<String> ALCANCES = List.of(SALDOS, MOVIMIENTOS, DEBITO_TDC);
    static final String ALCANCE_POR_DEFECTO = SALDOS + " " + MOVIMIENTOS;
    static final long EXPIRA_TOKEN_S = 7_776_000L;
    private static final Duration EXPIRA_CODIGO = Duration.ofMinutes(10);

    private final SecureRandom aleatorio = new SecureRandom();
    private final Map<String, CodigoEmitido> codigos = new ConcurrentHashMap<>();
    private final Map<String, TokenEmitido> tokens = new ConcurrentHashMap<>();

    /** Valida los alcances pedidos y los devuelve normalizados (separados por espacio). */
    public List<String> validarAlcances(Institucion inst, String scope) {
        String texto = (scope == null || scope.isBlank()) ? ALCANCE_POR_DEFECTO : scope;
        Set<String> alcances = new LinkedHashSet<>();
        for (String s : texto.trim().split("[\\s,+]+")) {
            if (!ALCANCES.contains(s)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ALCANCE_NO_PERMITIDO",
                        "Alcance desconocido: '" + s + "'");
            }
            alcances.add(s);
        }
        if (!inst.esBanco() && alcances.contains(DEBITO_TDC)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ALCANCE_NO_PERMITIDO",
                    inst.nombre() + " es una plataforma cripto y no permite el alcance '" + DEBITO_TDC + "'");
        }
        return new ArrayList<>(alcances);
    }

    public String emitirCodigo(Institucion inst, String cedula, List<String> alcances, String redirectUri) {
        String code = "cd_" + aleatorioHex(16);
        codigos.put(code, new CodigoEmitido(inst.codigo(), cedula, String.join(" ", alcances), redirectUri,
                Instant.now().plus(EXPIRA_CODIGO)));
        return code;
    }

    public Token canjear(Institucion inst, String grantType, String code, String redirectUri) {
        if (!"authorization_code".equals(grantType)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GRANT_NO_SOPORTADO",
                    "Solo se admite grant_type=authorization_code");
        }
        // remove ⇒ el código queda consumido en el primer intento (un solo uso)
        CodigoEmitido emitido = code == null ? null : codigos.remove(code);
        if (emitido == null
                || !emitido.institucion().equals(inst.codigo())
                || emitido.expira().isBefore(Instant.now())
                || (redirectUri != null && !redirectUri.equals(emitido.redirectUri()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODIGO_INVALIDO",
                    "El código de autorización no es válido, expiró o ya fue usado");
        }
        String token = "at_" + aleatorioHex(24);
        tokens.put(token, new TokenEmitido(inst.codigo(), emitido.cedula(), emitido.scope()));
        return new Token(token, "Bearer", EXPIRA_TOKEN_S, emitido.scope());
    }

    public void revocar(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }

    /** Valida el header {@code Authorization: Bearer ...} para la institución dada. */
    public TokenEmitido validar(Institucion inst, String authorization) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                ? authorization.substring(7).trim() : null;
        TokenEmitido emitido = token == null ? null : tokens.get(token);
        if (emitido == null || !emitido.institucion().equals(inst.codigo())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALIDO",
                    "Token ausente, revocado o inexistente");
        }
        return emitido;
    }

    private String aleatorioHex(int bytes) {
        byte[] b = new byte[bytes];
        aleatorio.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    record CodigoEmitido(String institucion, String cedula, String scope, String redirectUri, Instant expira) {
    }

    public record TokenEmitido(String institucion, String cedula, String scope) {
    }
}
