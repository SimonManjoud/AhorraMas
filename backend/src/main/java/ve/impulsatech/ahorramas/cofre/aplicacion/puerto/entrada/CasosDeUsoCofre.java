package ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import ve.impulsatech.ahorramas.cofre.api.CofreApi.ReglaCreada;

/** Puertos de entrada (casos de uso) del Cofre Protector. */
public final class CasosDeUsoCofre {

    private CasosDeUsoCofre() {
    }

    public interface GestionarMetas {
        VistaMeta crear(long idUsuario, NuevaMeta meta);

        List<VistaMeta> listar(long idUsuario);

        VistaMeta obtener(long idUsuario, long idMeta);

        VistaMeta cambiarEstatus(long idUsuario, long idMeta, String estatus);
    }

    /** Engranaje 1. */
    public interface ConsultarRutaDelDia {
        VistaRuta rutaDelDia(long idUsuario, long idMeta);

        List<VistaRuta> rutasDelDia(long idUsuario);
    }

    public interface MoverCofre {
        ResultadoAporte aportar(long idUsuario, long idMeta, long montoUsdCts, String origen, Long idCuentaOrigen);

        ResultadoRetiro retirar(long idUsuario, long idMeta, long montoUsdCts);

        List<VistaAporte> aportes(long idUsuario, long idMeta);
    }

    public interface GestionarReglas {
        ReglaCreada crear(long idUsuario, long idMeta, String tipo, Integer porcentajePb, Long multiploRedondeoCts, Long montoFijoUsdCts);

        List<ReglaCreada> listar(long idUsuario, long idMeta);

        ReglaCreada cambiarEstatus(long idUsuario, long idRegla, String estatus);
    }

    /** Reglas automáticas disparadas por movimientos bancarios (worker / eventos). */
    public interface EjecutarReglas {
        void ejecutarPorMovimiento(ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.MovimientoRegistrado movimiento);
    }

    /** Engranaje 2. */
    public interface ConsultarRacha {
        VistaRacha racha(long idUsuario);
    }

    /** Engranaje 4. */
    public interface GestionarSello {
        VistaSello sellar(long idUsuario, long idMeta, long montoUsdCts);

        VistaSello romper(long idUsuario, long idMeta);
    }

    /** Engranaje 3. */
    public interface ConsultarEspejo {
        VistaEspejo espejo(long idUsuario, Integer anio, Integer mes);
    }

    public record NuevaMeta(String nombre, String categoria, long montoObjetivoUsdCts, LocalDate fechaObjetivo, String frecuenciaCuota) {
    }

    public record VistaSello(long idSello, long montoSelladoUsdCts, LocalDate fechaLiberacion, String estatus, OffsetDateTime fechaSellado,
                             OffsetDateTime fechaRuptura) {
    }

    public record VistaRuta(long idMeta, String nombreMeta, LocalDate fecha, long montoSugeridoUsdCts, long montoSugeridoBsCts,
                            long tasaValorBsX10000, long aportadoHoyUsdCts, String estatus, int periodosRestantes, String mensaje) {
    }

    public record VistaMeta(long idMeta, String nombre, String categoria, long montoObjetivoUsdCts, LocalDate fechaObjetivo,
                            String frecuenciaCuota, String estatus, long acumuladoUsdCts, int progresoPb, OffsetDateTime fechaCreacion,
                            VistaSello sello, VistaRuta cuotaHoy) {
    }

    public record VistaAporte(long idAporte, OffsetDateTime fechaHora, String tipo, String origen, long montoUsdCts, long montoBsCts) {
    }

    public record TramoVista(int diasMinimos, int multiplicadorX100) {
    }

    public record DiaVista(LocalDate fecha, String tipo) {
    }

    public record VistaRacha(Long idRacha, LocalDate fechaInicio, int diasConsecutivos, int diasAporte, int diasColchonUsados,
                             int colchonesDisponibles, int multiplicadorX100, TramoVista siguienteTramo, boolean aporteHoy,
                             List<DiaVista> ultimosDias) {
    }

    public record ResultadoAporte(VistaAporte aporte, VistaMeta meta, VistaRacha racha, long puntosGanados) {
    }

    public record ResultadoRetiro(VistaAporte aporte, VistaMeta meta, boolean selloRoto) {
    }

    public record TasaVista(LocalDate fecha, long valorBsX10000) {
    }

    public record VistaEspejo(int anio, int mes, boolean provisional, long saldoCofresUsdCts, long perdidaEvitadaBsCts,
                              long perdidaEvitadaUsdCts, TasaVista tasaInicio, TasaVista tasaFin, long devaluacionPb,
                              Integer inflacionMensualPb, long totalBsCts, long totalUsdCts, String frase, String disclaimer) {
    }
}
