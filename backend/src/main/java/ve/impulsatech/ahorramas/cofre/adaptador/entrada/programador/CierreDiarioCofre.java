package ve.impulsatech.ahorramas.cofre.adaptador.entrada.programador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.cofre.api.CofreApi;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;

/** Worker: cierre del día anterior a las 00:05 hora de Caracas. */
@Component
@ConditionalOnProperty(name = "ahorra.worker.habilitado", havingValue = "true", matchIfMissing = true)
class CierreDiarioCofre {

    private static final Logger log = LoggerFactory.getLogger(CierreDiarioCofre.class);

    private final CofreApi cofre;
    private final Reloj reloj;

    CierreDiarioCofre(CofreApi cofre, Reloj reloj) {
        this.cofre = cofre;
        this.reloj = reloj;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "America/Caracas")
    void cerrarAyer() {
        try {
            cofre.cerrarDia(reloj.hoy().minusDays(1));
        } catch (RuntimeException e) {
            log.error("Falló el cierre diario del Cofre", e);
        }
    }
}
