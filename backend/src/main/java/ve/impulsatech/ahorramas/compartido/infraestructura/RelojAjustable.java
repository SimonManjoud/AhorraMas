package ve.impulsatech.ahorramas.compartido.infraestructura;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;

/**
 * Reloj del sistema en America/Caracas con un desfase opcional (solo modo demo / pruebas) para
 * poder "avanzar días" y demostrar la racha, los días colchón y la conciliación diaria.
 */
@Component
public class RelojAjustable implements Reloj {

    private final ZoneId zona;
    private final Clock base;
    private final AtomicLong desfaseSegundos = new AtomicLong();

    public RelojAjustable(@Value("${ahorra.zona-horaria:America/Caracas}") String zona) {
        this.zona = ZoneId.of(zona);
        this.base = Clock.system(this.zona);
    }

    @Override
    public OffsetDateTime ahora() {
        return OffsetDateTime.now(Clock.offset(base, Duration.ofSeconds(desfaseSegundos.get())));
    }

    @Override
    public LocalDate hoy() {
        return ahora().toLocalDate();
    }

    @Override
    public ZoneId zona() {
        return zona;
    }

    public void avanzar(Duration duracion) {
        desfaseSegundos.addAndGet(duracion.toSeconds());
    }

    public void reiniciar() {
        desfaseSegundos.set(0);
    }
}
