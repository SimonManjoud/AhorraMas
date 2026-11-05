package ve.impulsatech.ahorramas.cofre.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Operaciones del Cofre que otros módulos pueden invocar. */
public interface CofreApi {

    /**
     * Crea una regla de ahorro ya aprobada por el usuario con huella/PIN (R-13). Si nace de una
     * recomendación de IA, esta debe estar aceptada (lo garantiza el módulo de IA antes de invocar).
     */
    ReglaCreada crearReglaAprobada(long idUsuario, long idMeta, String tipo, long valor, Long idRecomendacion);

    int metasActivas(long idUsuario);

    /** Cierre de día (worker): racha/colchón, cuotas omitidas, reglas quincenales y reportes mensuales. */
    void cerrarDia(LocalDate fecha);

    record ReglaCreada(long idRegla, long idMeta, String tipo, Integer porcentajePb, Long multiploRedondeoCts, Long montoFijoUsdCts,
                       String estatus, OffsetDateTime fechaAprobacion) {
    }
}
