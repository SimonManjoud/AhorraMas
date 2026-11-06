package ve.impulsatech.ahorramas.pagos.api;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * KPIs técnicos del pago de TDC medidos en el prototipo (tasa de aprobación, latencia p95, TPS y tasa de reversos).
 * Se calculan sobre el ledger inmutable del módulo: {@code pago_tdc}, {@code historial_estado_pago} y {@code mensaje_suiche}.
 * Las tasas se expresan en puntos básicos (1 pb = 0,01 %) y las latencias en milisegundos: nunca punto flotante.
 */
public interface KpisApi {

    /**
     * @param rango  {@code hoy}, {@code 1h}, {@code 24h}, {@code 7d} o {@code 30d}; se ignora si llegan {@code desde} y {@code hasta}
     * @param bucket {@code auto}, {@code minuto}, {@code hora} o {@code dia}
     */
    TableroKpis kpis(String rango, OffsetDateTime desde, OffsetDateTime hasta, String bucket);

    record TableroKpis(Ventana ventana, Resumen resumen, Resumen resumenPrevio, List<LatenciaTipo> latenciasPorTipo, List<Bucket> series,
                       List<Conteo> estados, List<Conteo> motivosRechazo, List<Conteo> origenReversos, List<PagoReciente> pagosRecientes) {
    }

    record Ventana(OffsetDateTime desde, OffsetDateTime hasta, OffsetDateTime ahora, String bucket, long segundos, long segundosBucket) {
    }

    /**
     * Totales de la ventana. Definiciones:
     * <ul>
     *   <li>aprobados: pagos cuya captura aprobó el suiche (CAPTURADO, CONCILIADO, CON_DIFERENCIA o REVERSADO tras captura).</li>
     *   <li>rechazados: RECHAZADO, o REVERSADO sin captura (el suiche nunca llegó a capturar).</li>
     *   <li>enCurso: PENDIENTE, AUTORIZADO, EN_VERIFICACION (no cuentan para la tasa de aprobación).</li>
     *   <li>tasaAprobacionPb = aprobados / (aprobados + rechazados) · 10 000; null si no hay pagos terminales.</li>
     *   <li>tasaReversosPb = reversados / aprobados · 10 000; null si no hay aprobados.</li>
     *   <li>latenciaSuiche: ida y vuelta de cada mensaje al suiche con respuesta (los timeouts se cuentan aparte).</li>
     *   <li>latenciaExtremoAExtremo: desde la creación del pago hasta su primer estado de resultado (CAPTURADO, RECHAZADO o REVERSADO).</li>
     * </ul>
     */
    record Resumen(int pagos, int aprobados, int rechazados, int enCurso, int reversados, int conDiferencia, int conciliados, int timeouts,
                   int mensajesSuiche, Integer tasaAprobacionPb, Integer tasaReversosPb, Integer tasaTimeoutsPb,
                   Latencias latenciaSuiche, Latencias latenciaExtremoAExtremo,
                   int pagosSegundoPico, int pagosMinutoPico, int mensajesSegundoPico, long montoTotalCts, long comisionTotalCts) {
    }

    /** Percentiles por rango más cercano (nearest-rank) sobre las muestras con respuesta. */
    record Latencias(int muestras, long p50Ms, long p95Ms, long p99Ms, long maxMs) {
    }

    record LatenciaTipo(String tipo, int mensajes, int timeouts, Latencias latencias) {
    }

    /** Un intervalo de la serie temporal; los pagos se atribuyen al intervalo de su creación. */
    record Bucket(OffsetDateTime inicio, int pagos, int aprobados, int rechazados, int enCurso, int reversados, int timeouts,
                  Integer tasaAprobacionPb, Integer tasaReversosPb, Long p50Ms, Long p95Ms, int pagosSegundoPico) {
    }

    record Conteo(String clave, int cantidad) {
    }

    /** {@code escenario} = monto % 100: el código con el que el simulador del suiche decide el comportamiento del pago. */
    record PagoReciente(long idPago, String estado, long montoCts, int escenario, OffsetDateTime creacion, String referenciaSuiche,
                        Long latenciaAutorizacionMs, Long latenciaTotalMs, String motivoRechazo) {
    }
}
