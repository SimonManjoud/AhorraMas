package ve.impulsatech.ahorramas.notificaciones.adaptador.entrada.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.ServicioNotificaciones;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.puerto.PuertosNotificaciones.Notificacion;

/** Adaptador REST de notificaciones (contrato §7). */
@RestController
@RequestMapping("/api/v1/notificaciones")
class NotificacionesController {

    private final ServicioNotificaciones servicio;

    NotificacionesController(ServicioNotificaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    List<Notificacion> listar() {
        return servicio.listar(UsuarioActual.id());
    }

    @PostMapping("/{id}/leida")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leida(@PathVariable long id) {
        servicio.marcarLeida(UsuarioActual.id(), id);
    }
}
