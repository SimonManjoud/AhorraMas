package ve.impulsatech.ahorramas.pagos.dominio;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados del pago de TDC (R-14).
 * <pre>
 * PENDIENTE → AUTORIZADO → CAPTURADO → CONCILIADO
 * Alternos: RECHAZADO · EN_VERIFICACION · REVERSADO · CON_DIFERENCIA
 * </pre>
 * Ningún pago queda en un estado ambiguo: EN_VERIFICACION siempre se resuelve consultando al
 * suiche con la misma referencia (nunca se reenvía a ciegas).
 */
public enum EstadoPago {
    PENDIENTE, AUTORIZADO, CAPTURADO, CONCILIADO, RECHAZADO, EN_VERIFICACION, REVERSADO, CON_DIFERENCIA;

    private static final Map<EstadoPago, Set<EstadoPago>> TRANSICIONES = Map.of(
            PENDIENTE, EnumSet.of(AUTORIZADO, RECHAZADO, EN_VERIFICACION),
            EN_VERIFICACION, EnumSet.of(AUTORIZADO, CAPTURADO, RECHAZADO, REVERSADO),
            AUTORIZADO, EnumSet.of(CAPTURADO, RECHAZADO, EN_VERIFICACION, REVERSADO),
            CAPTURADO, EnumSet.of(CONCILIADO, CON_DIFERENCIA, REVERSADO),
            CON_DIFERENCIA, EnumSet.of(REVERSADO, CONCILIADO),
            CONCILIADO, EnumSet.noneOf(EstadoPago.class),
            RECHAZADO, EnumSet.noneOf(EstadoPago.class),
            REVERSADO, EnumSet.noneOf(EstadoPago.class));

    public boolean puedePasarA(EstadoPago destino) {
        return TRANSICIONES.get(this).contains(destino);
    }

    public boolean terminal() {
        return TRANSICIONES.get(this).isEmpty();
    }

    /** Estados en los que el dinero se movió o puede haberse movido (cuentan para el límite diario). */
    public boolean cuentaParaLimite() {
        return this != RECHAZADO;
    }
}
