package ve.impulsatech.ahorramas.notificaciones.aplicacion.puerto;

import java.time.OffsetDateTime;
import java.util.List;

/** Puertos de salida de Notificaciones. */
public final class PuertosNotificaciones {

    private PuertosNotificaciones() {
    }

    /** Proveedor de notificaciones (FCM/APNs, SMS, correo). */
    public interface CanalNotificaciones {
        boolean enviar(String canal, String destino, String titulo, String mensaje, String tipo);
    }

    public interface Repositorio {
        void insertar(long idUsuario, String canal, String tipo, String titulo, String mensaje, String estatus, OffsetDateTime fecha);

        List<Notificacion> listar(long idUsuario, int limite);

        boolean marcarLeida(long idUsuario, long idNotificacion, OffsetDateTime fecha);
    }

    public record Notificacion(long idNotificacion, String canal, String tipo, String titulo, String mensaje, OffsetDateTime fechaHoraEnvio,
                               String estatus, boolean leida) {
    }
}
