package ve.impulsatech.ahorramas.compartido.seguridad;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/**
 * Exige autenticación reforzada reciente (header {@code X-Step-Up}) en operaciones sensibles:
 * nivel {@code pin} para reglas, sello, retiros e IA; nivel {@code pin_otp} para pagos.
 */
@Component
public class VerificadorStepUp {

    public static final String CABECERA = "X-Step-Up";

    private final JwtDecoder decoder;

    public VerificadorStepUp(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    public void exigir(NivelStepUp requerido) {
        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String valor = req.getHeader(CABECERA);
        if (valor == null || valor.isBlank()) {
            throw requerido(requerido);
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(valor);
        } catch (JwtException e) {
            throw requerido(requerido);
        }
        if (!"stepup".equals(jwt.getClaimAsString(ServicioTokens.TIPO))
                || !String.valueOf(UsuarioActual.id()).equals(jwt.getSubject())
                || !NivelStepUp.de(jwt.getClaimAsString(ServicioTokens.NIVEL)).cubre(requerido)) {
            throw requerido(requerido);
        }
    }

    private static ErrorNegocio requerido(NivelStepUp nivel) {
        String msg = nivel == NivelStepUp.PIN_OTP
                ? "Confirma con tu PIN y el código OTP para continuar"
                : "Confirma con tu huella o PIN para continuar";
        return new ErrorNegocio(ErrorNegocio.Tipo.STEP_UP_REQUERIDO, "STEP_UP_REQUERIDO", msg, Map.of("nivelRequerido", nivel.codigo()));
    }
}
