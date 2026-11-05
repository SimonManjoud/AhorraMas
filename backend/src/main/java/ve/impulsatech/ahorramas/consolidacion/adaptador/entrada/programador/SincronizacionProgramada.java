package ve.impulsatech.ahorramas.consolidacion.adaptador.entrada.programador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.entrada.CasosDeUsoConsolidacion.SincronizarCuentas;

/** Worker de procesos (C4): sincroniza saldos y obtiene la tasa BCV del día periódicamente. */
@Component
@ConditionalOnProperty(name = "ahorra.worker.habilitado", havingValue = "true", matchIfMissing = true)
class SincronizacionProgramada {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionProgramada.class);

    private final SincronizarCuentas sincronizacion;
    private final TasasApi tasas;

    SincronizacionProgramada(SincronizarCuentas sincronizacion, TasasApi tasas) {
        this.sincronizacion = sincronizacion;
        this.tasas = tasas;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${ahorra.worker.intervalo-sincronizacion:PT15M}")
    void sincronizar() {
        try {
            tasas.tasaHoy();
            int n = sincronizacion.sincronizarTodos();
            log.info("Sincronización programada: {} consentimientos activos", n);
        } catch (RuntimeException e) {
            log.warn("Sincronización programada fallida: {}", e.getMessage());
        }
    }
}
