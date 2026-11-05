package ve.impulsatech.ahorramas.simuladores.notificaciones;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ve.impulsatech.ahorramas.simuladores.notificaciones.NotificacionesService.Notificacion;
import ve.impulsatech.ahorramas.simuladores.notificaciones.NotificacionesService.SolicitudNotificacion;

/** Pasarela de notificaciones simulada: push, SMS y correo (contrato §6). */
@RestController
public class NotificacionesController {

    private final NotificacionesService notificaciones;

    public NotificacionesController(NotificacionesService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @PostMapping("/notificaciones/{canal}")
    public Map<String, String> enviar(@PathVariable String canal, @RequestBody SolicitudNotificacion cuerpo) {
        Notificacion n = notificaciones.enviar(canal, cuerpo);
        return Map.of("id", n.id(), "estatus", n.estatus());
    }

    @GetMapping("/notificaciones")
    public List<Notificacion> ultimas(@RequestParam(required = false) String destino) {
        return notificaciones.ultimas(destino, 100);
    }
}
