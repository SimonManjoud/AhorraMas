package ve.impulsatech.ahorramas.compartido.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Puerto de tiempo. Todo el sistema obtiene "ahora" y "hoy" de aquí (zona America/Caracas),
 * lo que permite pruebas deterministas y el modo demo (avanzar días para ver la racha).
 */
public interface Reloj {

    OffsetDateTime ahora();

    LocalDate hoy();

    ZoneId zona();
}
