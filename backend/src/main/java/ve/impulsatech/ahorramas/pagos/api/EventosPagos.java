package ve.impulsatech.ahorramas.pagos.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Eventos de dominio del pago de TDC. */
public final class EventosPagos {

    private EventosPagos() {
    }

    /** Nuevo pago registrado en PENDIENTE (monitoreo AML en backoffice). */
    public record PagoSolicitado(long idUsuario, long idPago, long montoCts, OffsetDateTime fechaHora) {
    }

    /** Todo cambio de estado (alertas push al usuario). */
    public record PagoEstadoCambiado(long idUsuario, long idPago, String estadoAnterior, String estadoNuevo, long montoCts,
                                     String ultimos4Tarjeta, String motivo) {
    }

    /** Pago capturado: si es puntual, Puntos acredita los puntos financiados por el banco aliado (R-18). */
    public record PagoCapturado(long idUsuario, long idPago, long montoCts, boolean esPuntual, long idInstitucionEmisor,
                                int puntosPorPuntualidad, long feeBancoCts) {
    }

    /** Diferencia de conciliación: backoffice abre un caso. */
    public record DiferenciaConciliacion(Long idPago, Long idUsuario, String resultado, long diferenciaCts, String referenciaSuiche,
                                         LocalDate fechaConciliacion) {
    }
}
