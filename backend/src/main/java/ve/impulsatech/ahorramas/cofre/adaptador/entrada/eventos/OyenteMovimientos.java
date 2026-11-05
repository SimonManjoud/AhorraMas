package ve.impulsatech.ahorramas.cofre.adaptador.entrada.eventos;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.EjecutarReglas;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.MovimientoRegistrado;

/**
 * Adaptador de entrada por eventos: cada movimiento nuevo sincronizado del banco dispara las reglas
 * de ahorro automático. Asíncrono, transaccional y persistido (si falla se reintenta desde el outbox).
 */
@Component
class OyenteMovimientos {

    private final EjecutarReglas reglas;

    OyenteMovimientos(EjecutarReglas reglas) {
        this.reglas = reglas;
    }

    @ApplicationModuleListener
    void alRegistrarMovimiento(MovimientoRegistrado evento) {
        reglas.ejecutarPorMovimiento(evento);
    }
}
