package ve.impulsatech.ahorramas.pagos.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Operaciones de pagos para backoffice y worker. */
public interface PagosApi {

    List<ResumenPago> pagosPorEstado(String estado, int limite);

    /** Ejecuta (o devuelve, si ya se ejecutó) la conciliación del día contra el archivo del suiche. */
    ResumenConciliacion conciliar(LocalDate fecha);

    ResumenConciliacion conciliacion(LocalDate fecha);

    /** Resuelve pagos EN_VERIFICACION consultando al suiche con la misma referencia. */
    int resolverEnVerificacion();

    /** Monto y número de pagos no rechazados del usuario en un día (monitoreo AML). */
    ResumenDia resumenDelDia(long idUsuario, LocalDate fecha);

    record ResumenDia(long montoCts, int operaciones) {
    }

    record ResumenPago(long idPago, long idUsuario, String estado, long montoCts, long comisionCts, String referenciaSuiche,
                       String claveIdempotencia, OffsetDateTime fechaHoraCreacion, String motivoRechazo) {
    }

    record ResumenConciliacion(LocalDate fecha, String nombreArchivo, String hashArchivo, boolean hashValido, String estatus,
                               int totalRegistros, long montoTotalCts, int conciliados, int diferencias, List<LineaResultado> resultados) {
    }

    record LineaResultado(Long idPago, Integer linea, String resultado, Long diferenciaCts) {
    }
}
