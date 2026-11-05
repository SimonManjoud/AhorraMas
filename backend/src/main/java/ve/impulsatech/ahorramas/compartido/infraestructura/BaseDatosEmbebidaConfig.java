package ve.impulsatech.ahorramas.compartido.infraestructura;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.File;
import java.io.IOException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PostgreSQL embebido (binarios reales de PostgreSQL 17, sin Docker) para desarrollo local y pruebas.
 * Se activa con {@code ahorra.base-datos-embebida.habilitada=true} (perfil {@code local}).
 * Con el perfil {@code docker} se usa el PostgreSQL del docker-compose.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ahorra.base-datos-embebida.habilitada", havingValue = "true")
public class BaseDatosEmbebidaConfig {

    private static final Logger log = LoggerFactory.getLogger(BaseDatosEmbebidaConfig.class);

    @Bean(destroyMethod = "close")
    EmbeddedPostgres postgresEmbebido(@Value("${ahorra.base-datos-embebida.puerto:0}") int puerto,
                                      @Value("${ahorra.base-datos-embebida.directorio:}") String directorio) throws IOException {
        EmbeddedPostgres.Builder builder = EmbeddedPostgres.builder()
                .setServerConfig("timezone", "America/Caracas")
                .setServerConfig("max_connections", "120");
        if (puerto > 0) {
            builder.setPort(puerto);
        }
        if (directorio != null && !directorio.isBlank()) {
            File dir = new File(directorio).getAbsoluteFile();
            dir.mkdirs();
            builder.setDataDirectory(dir).setCleanDataDirectory(false);
        }
        EmbeddedPostgres pg = builder.start();
        log.info("PostgreSQL embebido iniciado en el puerto {}", pg.getPort());
        return pg;
    }

    /** Pool de conexiones (el DataSource del binario embebido no agrupa conexiones). */
    @Bean(destroyMethod = "close")
    DataSource dataSource(EmbeddedPostgres pg) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(pg.getJdbcUrl("postgres", "postgres"));
        cfg.setUsername("postgres");
        cfg.setMaximumPoolSize(20);
        cfg.setPoolName("ahorra-embebido");
        return new HikariDataSource(cfg);
    }
}
