package ve.impulsatech.ahorramas.compartido.dominio;

import java.util.Map;

/**
 * Error de negocio con un código estable que la app puede interpretar. El dominio no conoce HTTP:
 * el adaptador web traduce {@link Tipo} a un status.
 */
public class ErrorNegocio extends RuntimeException {

    public enum Tipo {
        VALIDACION, NO_ENCONTRADO, CONFLICTO, REGLA_NEGOCIO, PROHIBIDO, NO_AUTENTICADO,
        STEP_UP_REQUERIDO, KYC_PENDIENTE, LIMITE_EXCEDIDO, IDEMPOTENCY_KEY_REQUERIDA, SERVICIO_EXTERNO
    }

    private final Tipo tipo;
    private final String codigo;
    private final transient Map<String, Object> detalles;

    public ErrorNegocio(Tipo tipo, String codigo, String mensaje, Map<String, Object> detalles) {
        super(mensaje);
        this.tipo = tipo;
        this.codigo = codigo;
        this.detalles = detalles == null ? Map.of() : Map.copyOf(detalles);
    }

    public ErrorNegocio(Tipo tipo, String mensaje) {
        this(tipo, tipo.name(), mensaje, Map.of());
    }

    public static ErrorNegocio validacion(String mensaje) {
        return new ErrorNegocio(Tipo.VALIDACION, mensaje);
    }

    public static ErrorNegocio noEncontrado(String que) {
        return new ErrorNegocio(Tipo.NO_ENCONTRADO, que + " no encontrado");
    }

    public static ErrorNegocio conflicto(String mensaje) {
        return new ErrorNegocio(Tipo.CONFLICTO, mensaje);
    }

    /** Violación de una regla de negocio; el código identifica la regla (p. ej. "R-12"). */
    public static ErrorNegocio regla(String codigo, String mensaje) {
        return new ErrorNegocio(Tipo.REGLA_NEGOCIO, codigo, mensaje, Map.of());
    }

    public static ErrorNegocio prohibido(String mensaje) {
        return new ErrorNegocio(Tipo.PROHIBIDO, mensaje);
    }

    public static ErrorNegocio noAutenticado(String mensaje) {
        return new ErrorNegocio(Tipo.NO_AUTENTICADO, mensaje);
    }

    public static ErrorNegocio servicioExterno(String mensaje) {
        return new ErrorNegocio(Tipo.SERVICIO_EXTERNO, mensaje);
    }

    public Tipo tipo() {
        return tipo;
    }

    public String codigo() {
        return codigo;
    }

    public Map<String, Object> detalles() {
        return detalles;
    }
}
