package ve.impulsatech.ahorramas.compartido.seguridad;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** Acceso al usuario autenticado (para adaptadores web). */
public final class UsuarioActual {

    private UsuarioActual() {
    }

    public static long id() {
        String sub = jwt().getSubject();
        if (sub == null || sub.startsWith("op-")) {
            throw ErrorNegocio.prohibido("Operación disponible solo para usuarios");
        }
        return Long.parseLong(sub);
    }

    public static long idDispositivo() {
        Object disp = jwt().getClaim(ServicioTokens.DISPOSITIVO);
        if (disp == null) {
            throw ErrorNegocio.noAutenticado("Token sin dispositivo");
        }
        return ((Number) disp).longValue();
    }

    public static long idOperador() {
        String sub = jwt().getSubject();
        if (sub == null || !sub.startsWith("op-")) {
            throw ErrorNegocio.prohibido("Operación disponible solo para operadores");
        }
        return Long.parseLong(sub.substring(3));
    }

    public static String rolOperador() {
        return jwt().getClaimAsString(ServicioTokens.ROL);
    }

    static Jwt jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken t) {
            return t.getToken();
        }
        throw ErrorNegocio.noAutenticado("Se requiere autenticación");
    }
}
