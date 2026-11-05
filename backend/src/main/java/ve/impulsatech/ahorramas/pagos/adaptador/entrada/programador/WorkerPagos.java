package ve.impulsatech.ahorramas.pagos.adaptador.entrada.programador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.pagos.api.PagosApi;

/** Worker de pagos: resuelve EN_VERIFICACION cada 30 s y concilia el día anterior a la 01:00. */
@Component
@ConditionalOnProperty(name = "ahorra.worker.habilitado", havingValue = "true", matchIfMissing = true)
class WorkerPagos {

    private static final Logger log = LoggerFactory.getLogger(WorkerPagos.class);

    private final PagosApi pagos;
    private final Reloj reloj;

    WorkerPagos(PagosApi pagos, Reloj reloj) {
        this.pagos = pagos;
        this.reloj = reloj;
    }

    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "${ahorra.worker.intervalo-verificacion:PT30S}")
    void resolverEnVerificacion() {
        try {
            int n = pagos.resolverEnVerificacion();
            if (n > 0) {
                log.info("Pagos revisados contra el suiche: {}", n);
            }
        } catch (RuntimeException e) {
            log.warn("Falló la verificación de pagos: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 0 1 * * *", zone = "America/Caracas")
    void conciliarAyer() {
        try {
            var r = pagos.conciliar(reloj.hoy().minusDays(1));
            log.info("Conciliación {}: {} conciliados, {} diferencias", r.fecha(), r.conciliados(), r.diferencias());
        } catch (RuntimeException e) {
            log.error("Falló la conciliación diaria", e);
        }
    }
}
