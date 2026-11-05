package ve.impulsatech.ahorramas.backoffice.adaptador.entrada.eventos;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.backoffice.aplicacion.ServicioBackoffice;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.KycResuelto;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.DiferenciaConciliacion;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.PagoSolicitado;

/** Monitoreo de cumplimiento: casos por diferencias de conciliación y alertas AML. */
@Component
class OyentesBackoffice {

    private final ServicioBackoffice backoffice;

    OyentesBackoffice(ServicioBackoffice backoffice) {
        this.backoffice = backoffice;
    }

    @ApplicationModuleListener
    void alDetectarDiferencia(DiferenciaConciliacion e) {
        backoffice.abrirCasoPorDiferencia(e);
    }

    @ApplicationModuleListener
    void alSolicitarPago(PagoSolicitado e) {
        backoffice.monitorearPago(e);
    }

    @ApplicationModuleListener
    void alResolverKyc(KycResuelto e) {
        if (!e.aprobado()) {
            backoffice.alertaPorKyc(e.idUsuario(), e.motivo());
        }
    }

    /** Operadores de demostración (SSO simulado). Clave "demo". Solo en modo demo. */
    @Component
    @ConditionalOnProperty(name = "ahorra.demo.habilitado", havingValue = "true")
    static class OperadoresDemo implements ApplicationRunner {
        private final ServicioBackoffice backoffice;

        OperadoresDemo(ServicioBackoffice backoffice) {
            this.backoffice = backoffice;
        }

        @Override
        public void run(ApplicationArguments args) {
            backoffice.sembrarOperador("Sofía", "Rivas", "soporte@ahorramas.test", "soporte", "demo");
            backoffice.sembrarOperador("Carlos", "Mendoza", "conciliacion@ahorramas.test", "conciliacion", "demo");
            backoffice.sembrarOperador("Valentina", "Ortega", "cumplimiento@ahorramas.test", "oficial_cumpl", "demo");
            backoffice.sembrarOperador("Admin", "Ahorra", "admin@ahorramas.test", "admin", "demo");
        }
    }
}
