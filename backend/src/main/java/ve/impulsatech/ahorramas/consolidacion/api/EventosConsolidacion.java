package ve.impulsatech.ahorramas.consolidacion.api;

import java.time.OffsetDateTime;

/** Eventos de dominio de Consolidación. */
public final class EventosConsolidacion {

    private EventosConsolidacion() {
    }

    /** Movimiento nuevo leído del banco: dispara las reglas de ahorro automático del Cofre. */
    public record MovimientoRegistrado(long idUsuario, long idMovimiento, long idCuenta, String naturaleza, String clasificacion,
                                       long montoCts, String moneda, OffsetDateTime fechaHora, String descripcion) {
    }

    public record ConsentimientoActivado(long idUsuario, long idConsentimiento, long idInstitucion, String institucion,
                                         boolean permiteDebitoTdc) {
    }

    public record ConsentimientoRevocado(long idUsuario, long idConsentimiento, long idInstitucion, String institucion, String motivo) {
    }
}
