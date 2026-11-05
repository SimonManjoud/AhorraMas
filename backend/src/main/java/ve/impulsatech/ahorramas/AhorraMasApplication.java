package ve.impulsatech.ahorramas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Núcleo Ahorra+ — monolito modular.
 *
 * <p>Cada paquete de primer nivel es un módulo de negocio (M1–M7 del modelo E-R) con arquitectura
 * hexagonal interna: {@code dominio} (modelo puro), {@code aplicacion} (casos de uso y puertos) y
 * {@code adaptador} (web, persistencia, integraciones, eventos, programador). Spring Modulith verifica
 * que los módulos solo se comuniquen por sus paquetes {@code api} o por eventos de dominio.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
@Modulithic(systemName = "Ahorra+", sharedModules = "compartido")
public class AhorraMasApplication {

    public static void main(String[] args) {
        SpringApplication.run(AhorraMasApplication.class, args);
    }
}
