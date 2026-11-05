package ve.impulsatech.ahorramas.simuladores.comun;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

/** Reloj del simulador (zona America/Caracas) y formatos de fecha del contrato. */
@Component
public class Tiempo {

    public static final ZoneId ZONA = ZoneId.of("America/Caracas");

    private final Clock clock = Clock.system(ZONA);

    public OffsetDateTime ahora() {
        return OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    /** ISO-8601 con offset, sin fracciones de segundo: {@code 2026-11-04T10:15:30-04:00}. */
    public static String formatear(OffsetDateTime fechaHora) {
        return fechaHora.truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Fecha local a una hora dada en zona Caracas. */
    public static OffsetDateTime en(LocalDate fecha, int hora, int minuto) {
        return fecha.atTime(hora, minuto).atZone(ZONA).toOffsetDateTime();
    }
}
