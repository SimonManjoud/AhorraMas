package ve.impulsatech.ahorramas.cofre.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/**
 * Engranaje 4 · Sello de compromiso (R-12). Es lógico/registrado: el dinero sigue en el banco.
 * Romperlo antes de la fecha de la meta cuesta el multiplicador de racha, nunca dinero.
 */
public record SelloCompromiso(Long idSello, long idMeta, long montoSelladoUsdCts, OffsetDateTime fechaSellado, LocalDate fechaLiberacion,
                              String estatus, OffsetDateTime fechaRuptura) {

    public static SelloCompromiso sellar(MetaAhorro meta, long monto, long acumuladoUsdCts, boolean yaTieneSelloActivo, OffsetDateTime ahora) {
        if (!meta.activa()) {
            throw ErrorNegocio.regla("R-12", "Solo puedes sellar una meta activa");
        }
        if (yaTieneSelloActivo) {
            throw ErrorNegocio.conflicto("Esta meta ya tiene un sello activo");
        }
        if (monto <= 0) {
            throw ErrorNegocio.validacion("El monto a sellar debe ser positivo");
        }
        if (monto > acumuladoUsdCts) {
            throw ErrorNegocio.regla("R-12", "Solo puedes sellar hasta lo que ya tienes en el cofre");
        }
        return new SelloCompromiso(null, meta.idMeta(), monto, ahora, meta.fechaObjetivo(), "activo", null);
    }

    public boolean activo() {
        return "activo".equals(estatus);
    }

    /** Un retiro rompe el sello si deja el cofre por debajo de lo sellado antes de la fecha de liberación. */
    public boolean seRompeCon(long acumuladoDespuesUsdCts, LocalDate hoy) {
        return activo() && hoy.isBefore(fechaLiberacion) && acumuladoDespuesUsdCts < montoSelladoUsdCts;
    }

    public SelloCompromiso romper(OffsetDateTime ahora) {
        if (!activo()) {
            throw ErrorNegocio.regla("R-12", "El sello no está activo");
        }
        return new SelloCompromiso(idSello, idMeta, montoSelladoUsdCts, fechaSellado, fechaLiberacion, "roto", ahora);
    }

    public SelloCompromiso liberar() {
        return new SelloCompromiso(idSello, idMeta, montoSelladoUsdCts, fechaSellado, fechaLiberacion, "liberado", null);
    }
}
