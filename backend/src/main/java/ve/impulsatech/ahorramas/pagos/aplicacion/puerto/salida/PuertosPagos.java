package ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis;
import ve.impulsatech.ahorramas.pagos.dominio.Conciliador;
import ve.impulsatech.ahorramas.pagos.dominio.EstadoPago;
import ve.impulsatech.ahorramas.pagos.dominio.PagoTdc;
import ve.impulsatech.ahorramas.pagos.dominio.TarifaComision;

/** Puertos de salida del módulo de pagos. */
public final class PuertosPagos {

    private PuertosPagos() {
    }

    /** Suiche interbancario (mensajería interbancaria; simulado en el MVP). */
    public interface Suiche {
        Beneficiario consultarBeneficiario(String bancoEmisor, String refTarjeta);

        RespuestaSuiche autorizar(SolicitudAutorizacion solicitud);

        RespuestaSuiche capturar(String referencia, String codigoAutorizacion);

        RespuestaSuiche reversar(String referencia, String motivo);

        OperacionSuiche consultar(String referencia);

        ArchivoLiquidacion liquidacion(LocalDate fecha);
    }

    /** El suiche no respondió a tiempo: el resultado es desconocido (nunca asumir fallo). */
    public static class SuicheSinRespuesta extends RuntimeException {
        public SuicheSinRespuesta(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    /** Banco emisor: tarjetas del usuario por consentimiento (alias + últimos 4, nunca PAN). */
    public interface EmisorTarjetas {
        List<TarjetaExterna> tarjetas(String codigoBanco, String token);
    }

    public interface Repositorio {
        Optional<TarifaComision> tarifaVigente(LocalDate hoy);

        long guardarTarjeta(long idUsuario, long idInstitucion, TarjetaExterna t, OffsetDateTime ahora);

        void guardarEstadoCuenta(long idTarjeta, EstadoCuentaExterno e);

        List<TarjetaGuardada> tarjetas(long idUsuario);

        Optional<TarjetaGuardada> tarjeta(long idTarjeta);

        void confirmarTitular(long idTarjeta, String titular);

        /** Inserta el pago (UNIQUE clave_idempotencia) y su primer historial; vacío si la clave ya existía. */
        Optional<PagoTdc> insertarPago(PagoTdc pago);

        Optional<PagoTdc> pago(long idPago);

        Optional<PagoTdc> pagoParaActualizar(long idPago);

        Optional<PagoTdc> pagoPorClave(String clave);

        List<PagoTdc> pagos(long idUsuario, int limite);

        List<PagoTdc> pagosPorEstado(EstadoPago estado, int limite);

        void actualizarPago(PagoTdc pago, OffsetDateTime ahora);

        void insertarHistorial(long idPago, EstadoPago anterior, EstadoPago nuevo, String actor, String referenciaSuiche, OffsetDateTime fecha);

        List<Historial> historial(long idPago);

        void registrarMensaje(long idPago, String tipo, OffsetDateTime envio, OffsetDateTime respuesta, String resultado, String codigo);

        UsoDiario usoDelDia(long idUsuario, OffsetDateTime desde, OffsetDateTime hasta);

        int intentosDesde(long idUsuario, OffsetDateTime desde);

        void bloquearUsuario(long idUsuario);

        Optional<ArchivoGuardado> archivo(LocalDate fecha);

        long insertarArchivo(ArchivoLiquidacion a, OffsetDateTime recepcion, String estatus);

        void actualizarEstatusArchivo(long idArchivo, String estatus);

        List<PagoTdc> pagosCapturadosEntre(OffsetDateTime desde, OffsetDateTime hasta);

        List<PagoTdc> pagosPorReferencias(List<String> referenciasSuiche, List<String> claves);

        void insertarResultado(long idArchivo, LocalDate fecha, Conciliador.Resultado r);

        List<Conciliador.Resultado> resultados(long idArchivo);
    }

    /** Lecturas del ledger para el tablero de KPIs: pagos creados en la ventana, con sus mensajes al suiche y su historial. */
    public interface Metricas {
        List<CalculadoraKpis.Pago> pagosCreadosEntre(OffsetDateTime desde, OffsetDateTime hasta);

        List<CalculadoraKpis.Mensaje> mensajesDePagosCreadosEntre(OffsetDateTime desde, OffsetDateTime hasta);

        List<CalculadoraKpis.Transicion> transicionesDePagosCreadosEntre(OffsetDateTime desde, OffsetDateTime hasta);
    }

    public record Beneficiario(String titular, String ultimos4, String refTarjeta) {
    }

    public record SolicitudAutorizacion(String referencia, String bancoOrigen, String refCuentaOrigen, String bancoEmisor, String refTarjeta,
                                        long montoCts, String moneda) {
    }

    public record RespuestaSuiche(String referencia, String resultado, String codigoRespuesta, String codigoAutorizacion,
                                  String referenciaSuiche, String motivo) {
        public boolean aprobado() {
            return "aprobado".equals(resultado);
        }
    }

    public record OperacionSuiche(String referencia, String estado, String referenciaSuiche, String codigoAutorizacion, Long montoCts) {
    }

    public record RegistroArchivo(int linea, String referencia, String referenciaSuiche, long montoCts, String estado, OffsetDateTime fechaHoraOperacion) {
    }

    public record ArchivoLiquidacion(LocalDate fechaLiquidacion, String nombreArchivo, String hashArchivo, boolean hashValido,
                                     int totalRegistros, long montoTotalCts, List<RegistroArchivo> registros) {
    }

    public record ArchivoGuardado(long idArchivo, LocalDate fecha, String nombreArchivo, String hashArchivo, int totalRegistros,
                                  long montoTotalCts, String estatus) {
    }

    /** {@code marca}: visa|mastercard, o null si el emisor no la informa. */
    public record TarjetaExterna(String refTarjeta, String ultimos4, String titular, EstadoCuentaExterno estadoCuenta, String marca) {
    }

    public record EstadoCuentaExterno(LocalDate fechaCorte, LocalDate fechaLimitePago, long saldoTotalCts, long pagoMinimoCts) {
    }

    public record TarjetaGuardada(long idTarjeta, long idUsuario, long idInstitucion, String refTarjetaEmisor, String ultimos4,
                                  String titularConfirmado, boolean esPropia, String alias, String estatus, Long idEstadoCuenta,
                                  EstadoCuentaExterno estadoCuenta, String marca) {
    }

    public record Historial(int secuencia, String estadoAnterior, String estadoNuevo, OffsetDateTime fechaHora, String actor,
                            String referenciaSuiche) {
    }

    public record UsoDiario(long montoCts, int operaciones) {
    }
}
