package ve.impulsatech.ahorramas.pagos.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** Agregado PAGO_TDC. Cada cambio de estado se valida contra la máquina de estados (R-14). */
public record PagoTdc(
        Long idPago,
        long idUsuario,
        long idTarjeta,
        long idCuentaOrigen,
        Long idEstadoCuenta,
        long idTarifa,
        String claveIdempotencia,
        long montoCts,
        long comisionCts,
        OffsetDateTime fechaHoraCreacion,
        EstadoPago estado,
        boolean beneficiarioConfirmado,
        String referenciaSuiche,
        String codigoAutorizacion,
        String motivoRechazo,
        Boolean esPuntual,
        OffsetDateTime fechaHoraCaptura) {

    public static final int LARGO_MAXIMO_CLAVE = 64;

    /** Crea el pago en PENDIENTE. La comisión se calcula una sola vez aquí (R-17). */
    public static PagoTdc nuevo(long idUsuario, long idTarjeta, long idCuentaOrigen, Long idEstadoCuenta, TarifaComision tarifa,
                                String claveIdempotencia, long montoCts, boolean beneficiarioConfirmado, OffsetDateTime ahora) {
        if (claveIdempotencia == null || claveIdempotencia.isBlank() || claveIdempotencia.length() > LARGO_MAXIMO_CLAVE) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.IDEMPOTENCY_KEY_REQUERIDA, "Idempotency-Key inválida (1–64 caracteres)");
        }
        if (montoCts <= 0) {
            throw ErrorNegocio.validacion("El monto debe ser positivo");
        }
        if (!beneficiarioConfirmado) {
            throw ErrorNegocio.regla("R-16", "Debes confirmar el titular y los últimos 4 dígitos de la tarjeta");
        }
        return new PagoTdc(null, idUsuario, idTarjeta, idCuentaOrigen, idEstadoCuenta, tarifa.idTarifa(), claveIdempotencia.trim(), montoCts,
                tarifa.calcular(montoCts), ahora, EstadoPago.PENDIENTE, true, null, null, null, null, null);
    }

    public PagoTdc pasarA(EstadoPago destino) {
        if (!estado.puedePasarA(destino)) {
            throw ErrorNegocio.conflicto("Transición inválida del pago: " + estado + " → " + destino);
        }
        return con(destino, referenciaSuiche, codigoAutorizacion, motivoRechazo, esPuntual, fechaHoraCaptura);
    }

    public PagoTdc autorizado(String codigoAutorizacion, String referenciaSuiche) {
        return pasarA(EstadoPago.AUTORIZADO).con(EstadoPago.AUTORIZADO, referenciaSuiche != null ? referenciaSuiche : this.referenciaSuiche,
                codigoAutorizacion, null, null, null);
    }

    /** R-18: puntual si se captura antes o en la fecha límite de pago del estado de cuenta. */
    public PagoTdc capturado(String referenciaSuiche, OffsetDateTime fecha, LocalDate fechaLimitePago) {
        boolean puntual = fechaLimitePago != null && !fecha.toLocalDate().isAfter(fechaLimitePago);
        return pasarA(EstadoPago.CAPTURADO).con(EstadoPago.CAPTURADO, referenciaSuiche != null ? referenciaSuiche : this.referenciaSuiche,
                codigoAutorizacion, null, puntual, fecha);
    }

    public PagoTdc rechazado(String motivo) {
        return pasarA(EstadoPago.RECHAZADO).con(EstadoPago.RECHAZADO, referenciaSuiche, codigoAutorizacion, motivo, esPuntual, fechaHoraCaptura);
    }

    /** R-17: un pago rechazado o reversado no cobra comisión. */
    public long comisionCobrada() {
        return estado == EstadoPago.RECHAZADO || estado == EstadoPago.REVERSADO ? 0 : comisionCts;
    }

    public PagoTdc conId(long id) {
        return new PagoTdc(id, idUsuario, idTarjeta, idCuentaOrigen, idEstadoCuenta, idTarifa, claveIdempotencia, montoCts, comisionCts,
                fechaHoraCreacion, estado, beneficiarioConfirmado, referenciaSuiche, codigoAutorizacion, motivoRechazo, esPuntual, fechaHoraCaptura);
    }

    private PagoTdc con(EstadoPago e, String ref, String codAut, String motivo, Boolean puntual, OffsetDateTime captura) {
        return new PagoTdc(idPago, idUsuario, idTarjeta, idCuentaOrigen, idEstadoCuenta, idTarifa, claveIdempotencia, montoCts, comisionCts,
                fechaHoraCreacion, e, beneficiarioConfirmado, ref, codAut, motivo, puntual, captura);
    }
}
