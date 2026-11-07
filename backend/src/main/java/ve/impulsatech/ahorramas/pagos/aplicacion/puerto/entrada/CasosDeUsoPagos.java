package ve.impulsatech.ahorramas.pagos.aplicacion.puerto.entrada;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Casos de uso del pago de TDC (F2). */
public final class CasosDeUsoPagos {

    private CasosDeUsoPagos() {
    }

    public interface GestionarTarjetas {
        List<VistaTarjeta> tarjetas(long idUsuario);

        BeneficiarioConfirmable confirmarBeneficiario(long idUsuario, long idTarjeta);
    }

    public interface PagarTarjeta {
        List<CuentaOrigen> cuentasOrigen(long idUsuario);

        Cotizacion cotizar(long montoCts);

        ResultadoPago pagar(long idUsuario, String idempotencyKey, ComandoPago comando);

        VistaPago obtener(long idUsuario, long idPago);

        List<VistaPago> listar(long idUsuario);

        UsoLimites limites(long idUsuario);
    }

    public record ComandoPago(long idTarjeta, long idCuentaOrigen, long montoCts, boolean beneficiarioConfirmado) {
    }

    public record EstadoCuentaVista(LocalDate fechaCorte, LocalDate fechaLimitePago, long saldoTotalCts, long pagoMinimoCts) {
    }

    public record VistaTarjeta(long idTarjeta, long idInstitucion, String banco, String ultimos4, String alias, String titularConfirmado,
                               boolean esPropia, EstadoCuentaVista estadoCuenta, String marca) {
    }

    public record BeneficiarioConfirmable(String titular, String ultimos4) {
    }

    public record CuentaOrigen(long idCuenta, String banco, String ultimos4, String alias, long saldoCts) {
    }

    public record Cotizacion(long montoCts, long comisionCts, long totalCts) {
    }

    public record HistorialVista(int secuencia, String estadoAnterior, String estadoNuevo, OffsetDateTime fechaHora, String actor) {
    }

    public record VistaPago(long idPago, String estado, long montoCts, long comisionCts, String referenciaSuiche, String codigoAutorizacion,
                            Boolean esPuntual, String motivoRechazo, OffsetDateTime fechaHoraCreacion, String mensaje, String ultimos4Tarjeta,
                            List<HistorialVista> historial) {
    }

    public record ResultadoPago(VistaPago pago, boolean nuevo) {
    }

    public record UsoLimites(long montoMaxDiarioCts, int maxOperacionesDiarias, long usadoHoyCts, int operacionesHoy) {
    }
}
