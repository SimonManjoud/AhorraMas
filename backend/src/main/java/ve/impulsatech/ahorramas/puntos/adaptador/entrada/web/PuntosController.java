package ve.impulsatech.ahorramas.puntos.adaptador.entrada.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.puntos.aplicacion.puerto.entrada.CasosDeUsoPuntos.*;

/** Adaptador REST de Puntos Ahorro (contrato §5). */
@RestController
@RequestMapping("/api/v1")
class PuntosController {

    private final ConsultarPuntos consultar;
    private final CanjearRecompensa canjear;

    PuntosController(ConsultarPuntos consultar, CanjearRecompensa canjear) {
        this.consultar = consultar;
        this.canjear = canjear;
    }

    @GetMapping("/puntos")
    ResumenPuntos puntos() {
        return consultar.resumen(UsuarioActual.id());
    }

    @GetMapping("/puntos/movimientos")
    List<MovimientoPuntos> movimientos() {
        return consultar.movimientos(UsuarioActual.id());
    }

    @GetMapping("/recompensas")
    List<RecompensaVista> recompensas() {
        return consultar.recompensas();
    }

    @PostMapping("/recompensas/{id}/canjes")
    @ResponseStatus(HttpStatus.CREATED)
    CanjeVista canjear(@PathVariable long id) {
        return canjear.canjear(UsuarioActual.id(), id);
    }

    @GetMapping("/canjes")
    List<CanjeVista> canjes() {
        return consultar.canjes(UsuarioActual.id());
    }
}
