package ve.impulsatech.ahorramas.identidad.dominio;

import java.time.OffsetDateTime;
import java.util.UUID;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** Desafío OTP de un solo uso, con expiración y máximo de intentos. El código se guarda como hash. */
public record DesafioOtp(UUID idDesafio, long idUsuario, String proposito, String codigoHash,
                         OffsetDateTime expiraEn, boolean usado, int intentos) {

    public static final int MAX_INTENTOS = 3;

    /** Verifica el código; devuelve el desafío actualizado (usado o con un intento más). */
    public Resultado verificar(String codigoHashIngresado, OffsetDateTime ahora) {
        if (usado) {
            throw ErrorNegocio.regla("OTP_USADO", "Este código ya fue utilizado. Solicita uno nuevo.");
        }
        if (ahora.isAfter(expiraEn)) {
            throw ErrorNegocio.regla("OTP_EXPIRADO", "El código expiró. Solicita uno nuevo.");
        }
        if (intentos >= MAX_INTENTOS) {
            throw ErrorNegocio.regla("OTP_BLOQUEADO", "Demasiados intentos. Solicita un nuevo código.");
        }
        boolean ok = codigoHash.equals(codigoHashIngresado);
        DesafioOtp actualizado = new DesafioOtp(idDesafio, idUsuario, proposito, codigoHash, expiraEn, ok, intentos + (ok ? 0 : 1));
        return new Resultado(ok, actualizado);
    }

    public record Resultado(boolean valido, DesafioOtp desafio) {
    }
}
