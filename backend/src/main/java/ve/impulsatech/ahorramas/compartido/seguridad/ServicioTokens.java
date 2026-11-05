package ve.impulsatech.ahorramas.compartido.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;

/**
 * Emisión de tokens de vida corta (equivalente local del Servicio de Identidad OIDC del C4).
 * <ul>
 *   <li>{@code sesion}: sesión completa tras huella/PIN.</li>
 *   <li>{@code saldo}: alcance {@code saldo:leer}, ligado al dispositivo (F3 · saldo rápido).</li>
 *   <li>{@code stepup}: prueba reciente de PIN (y OTP para pagos), 5 minutos.</li>
 *   <li>{@code operador}: backoffice (SSO simulado).</li>
 * </ul>
 */
@Component
public class ServicioTokens {

    public static final String TIPO = "typ";
    public static final String DISPOSITIVO = "disp";
    public static final String NIVEL = "nivel";
    public static final String ROL = "rol";

    private final JwtEncoder encoder;
    private final Reloj reloj;
    private final int minutosSesion;
    private final int minutosStepUp;
    private final int diasSaldoRapido;

    public ServicioTokens(@Value("${ahorra.seguridad.jwt-secreto}") String secreto,
                          @Value("${ahorra.seguridad.minutos-sesion}") int minutosSesion,
                          @Value("${ahorra.seguridad.minutos-step-up}") int minutosStepUp,
                          @Value("${ahorra.seguridad.dias-saldo-rapido}") int diasSaldoRapido,
                          Reloj reloj) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave(secreto)));
        this.reloj = reloj;
        this.minutosSesion = minutosSesion;
        this.minutosStepUp = minutosStepUp;
        this.diasSaldoRapido = diasSaldoRapido;
    }

    static SecretKey clave(String secretoBase64) {
        return new SecretKeySpec(Base64.getDecoder().decode(secretoBase64), "HmacSHA256");
    }

    public TokenEmitido sesion(long idUsuario, long idDispositivo) {
        return emitir(String.valueOf(idUsuario), "sesion", "sesion", Duration.ofMinutes(minutosSesion), idDispositivo, null, null);
    }

    public TokenEmitido saldoRapido(long idUsuario, long idDispositivo) {
        return emitir(String.valueOf(idUsuario), "saldo", "saldo:leer", Duration.ofDays(diasSaldoRapido), idDispositivo, null, null);
    }

    public TokenEmitido stepUp(long idUsuario, long idDispositivo, NivelStepUp nivel) {
        return emitir(String.valueOf(idUsuario), "stepup", "stepup", Duration.ofMinutes(minutosStepUp), idDispositivo, nivel, null);
    }

    public TokenEmitido operador(long idOperador, String rol) {
        return emitir("op-" + idOperador, "operador", "operador", Duration.ofHours(8), null, null, rol);
    }

    private TokenEmitido emitir(String sujeto, String tipo, String alcance, Duration vida, Long idDispositivo,
                                NivelStepUp nivel, String rol) {
        // Los tokens usan el instante real (no el reloj demo) para que la validación de expiración sea coherente.
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(vida);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("ahorra-mas")
                .subject(sujeto)
                .issuedAt(ahora)
                .expiresAt(expira)
                .id(UUID.randomUUID().toString())
                .claim(TIPO, tipo)
                .claim("scope", alcance);
        if (idDispositivo != null) {
            claims.claim(DISPOSITIVO, idDispositivo);
        }
        if (nivel != null) {
            claims.claim(NIVEL, nivel.codigo());
        }
        if (rol != null) {
            claims.claim(ROL, rol);
        }
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecera, claims.build())).getTokenValue();
        return new TokenEmitido(token, OffsetDateTime.ofInstant(expira, reloj.zona()));
    }

    public record TokenEmitido(String token, OffsetDateTime expiraEn) {
    }
}
