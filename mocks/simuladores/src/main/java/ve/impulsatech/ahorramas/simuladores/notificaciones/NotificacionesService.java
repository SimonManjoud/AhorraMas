package ve.impulsatech.ahorramas.simuladores.notificaciones;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.comun.Tiempo;

/** Bandeja en memoria de push, SMS y correos "enviados" (nada sale realmente). */
@Service
public class NotificacionesService {

    static final Set<String> CANALES = Set.of("push", "sms", "correo");
    static final Set<String> TIPOS = Set.of("otp", "pago", "meta", "racha", "cuota", "seguridad", "movimiento");
    private static final int MAXIMO_GUARDADAS = 1_000;

    private final Tiempo tiempo;
    private final Deque<Notificacion> enviadas = new ConcurrentLinkedDeque<>();
    private final AtomicLong secuencia = new AtomicLong();

    public NotificacionesService(Tiempo tiempo) {
        this.tiempo = tiempo;
    }

    public Notificacion enviar(String canal, SolicitudNotificacion s) {
        String c = canal == null ? "" : canal.toLowerCase(Locale.ROOT);
        if (!CANALES.contains(c)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANAL_INVALIDO", "Canales válidos: push, sms, correo");
        }
        if (s == null || vacio(s.destino()) || vacio(s.mensaje())) {
            throw ApiException.solicitudInvalida("Se requieren 'destino' y 'mensaje'");
        }
        String tipo = s.tipo() == null ? null : s.tipo().toLowerCase(Locale.ROOT);
        if (tipo != null && !TIPOS.contains(tipo)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIPO_INVALIDO",
                    "Tipos válidos: otp, pago, meta, racha, cuota, seguridad, movimiento");
        }
        Notificacion n = new Notificacion(String.format("NTF-%06d", secuencia.incrementAndGet()), c, s.destino(),
                s.titulo(), s.mensaje(), tipo, "enviada", Tiempo.formatear(tiempo.ahora()));
        enviadas.addFirst(n);
        while (enviadas.size() > MAXIMO_GUARDADAS) {
            enviadas.pollLast();
        }
        return n;
    }

    /** Las más recientes primero; {@code destino} nulo ⇒ todas. */
    public List<Notificacion> ultimas(String destino, int maximo) {
        List<Notificacion> lista = new ArrayList<>();
        for (Iterator<Notificacion> it = enviadas.iterator(); it.hasNext() && lista.size() < maximo; ) {
            Notificacion n = it.next();
            if (destino == null || destino.isBlank() || n.destino().equals(destino)) {
                lista.add(n);
            }
        }
        return lista;
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    public record SolicitudNotificacion(String destino, String titulo, String mensaje, String tipo) {
    }

    public record Notificacion(String id, String canal, String destino, String titulo, String mensaje, String tipo,
                               String estatus, String fechaHora) {
    }
}
