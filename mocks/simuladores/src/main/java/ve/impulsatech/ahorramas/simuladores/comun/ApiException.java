package ve.impulsatech.ahorramas.simuladores.comun;

import org.springframework.http.HttpStatus;

/** Error de negocio del simulador; se traduce a {@code {"codigo","mensaje"}} con el status indicado. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }

    public static ApiException solicitudInvalida(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", mensaje);
    }

    public static ApiException noEncontrado(String codigo, String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
