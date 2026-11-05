package ve.impulsatech.ahorramas.compartido.auditoria;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Puerto del registro de auditoría (C4: almacenamiento WORM). Quién hizo qué y cuándo:
 * logins, consentimientos, pagos, cambios de límites, decisiones de IA. <b>Nunca PII en claro.</b>
 */
public interface Auditoria {

    enum Actor { usuario, sistema, suiche, operador }

    void registrar(Actor actor, Object actorId, String accion, String entidad, Object idEntidad, Map<String, ?> detalle);

    default void registrar(Actor actor, Object actorId, String accion, String entidad, Object idEntidad) {
        registrar(actor, actorId, accion, entidad, idEntidad, Map.of());
    }

    List<EventoAuditoria> ultimos(int limite);

    /** Recorre la cadena de hashes y confirma que ningún evento fue alterado. */
    boolean cadenaIntegra();

    record EventoAuditoria(long idEvento, OffsetDateTime fechaHora, String actorTipo, String actorId, String accion,
                           String entidad, String idEntidad, String detalle, String hashActual) {
    }
}
