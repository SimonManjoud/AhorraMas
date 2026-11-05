package ve.impulsatech.ahorramas.puntos.aplicacion.puerto.entrada;

import java.time.OffsetDateTime;
import java.util.List;

/** Casos de uso de Puntos Ahorro. */
public final class CasosDeUsoPuntos {

    private CasosDeUsoPuntos() {
    }

    public interface ConsultarPuntos {
        ResumenPuntos resumen(long idUsuario);

        List<MovimientoPuntos> movimientos(long idUsuario);

        List<RecompensaVista> recompensas();

        List<CanjeVista> canjes(long idUsuario);
    }

    public interface CanjearRecompensa {
        CanjeVista canjear(long idUsuario, long idRecompensa);
    }

    /** Acreditaciones disparadas por eventos de otros módulos (idempotentes por clave de origen). */
    public interface AcreditarPuntos {
        void abrirCuenta(long idUsuario);

        void acreditarDesdeSistema(long idUsuario, long puntos, String concepto, String descripcion, String claveOrigen);

        void acreditarDesdeBanco(long idUsuario, long idInstitucion, long puntos, String descripcion, String claveOrigen);
    }

    public record NivelVista(String nombre, OffsetDateTime desde) {
    }

    public record SiguienteNivel(String nombre, long puntosMinimos, long puntosFaltantes) {
    }

    public record ResumenPuntos(long saldo, long puntosGanadosTotal, NivelVista nivel, SiguienteNivel siguienteNivel) {
    }

    public record MovimientoPuntos(long idTransaccion, OffsetDateTime fechaHora, String concepto, String descripcion, long puntos) {
    }

    public record RecompensaVista(long idRecompensa, String descripcion, long costoPuntos, String comercio, String nivelMinimo, Integer stockDisponible) {
    }

    public record CanjeVista(long idCanje, String recompensa, String codigoCanje, long puntosCanjeados, String estatus, OffsetDateTime fechaHora) {
    }
}
