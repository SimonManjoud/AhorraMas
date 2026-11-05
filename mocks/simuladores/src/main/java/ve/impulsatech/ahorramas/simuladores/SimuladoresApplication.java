package ve.impulsatech.ahorramas.simuladores;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Simuladores de los sistemas externos de Ahorra+ (BCV, Open Banking, suiche, KYC, IA y
 * notificaciones). Todo el estado vive en memoria; los datos son deterministas por cédula/fecha.
 */
@SpringBootApplication
public class SimuladoresApplication {

    public static void main(String[] args) {
        SpringApplication.run(SimuladoresApplication.class, args);
    }
}
