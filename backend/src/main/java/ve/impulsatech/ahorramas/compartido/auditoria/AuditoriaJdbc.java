package ve.impulsatech.ahorramas.compartido.auditoria;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;

/** Adaptador de auditoría sobre una tabla PostgreSQL append-only con encadenamiento de hashes (trigger). */
@Component
class AuditoriaJdbc implements Auditoria {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Reloj reloj;

    AuditoriaJdbc(JdbcClient jdbc, JsonMapper json, Reloj reloj) {
        this.jdbc = jdbc;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    public void registrar(Actor actor, Object actorId, String accion, String entidad, Object idEntidad, Map<String, ?> detalle) {
        jdbc.sql("""
                INSERT INTO auditoria.evento (fecha_hora, actor_tipo, actor_id, accion, entidad, id_entidad, detalle, hash_anterior, hash_actual)
                VALUES (:fecha, :actor, :actorId, :accion, :entidad, :idEntidad, CAST(:detalle AS jsonb), '', '')
                """)
                .param("fecha", reloj.ahora())
                .param("actor", actor.name())
                .param("actorId", actorId == null ? null : String.valueOf(actorId))
                .param("accion", accion)
                .param("entidad", entidad)
                .param("idEntidad", idEntidad == null ? null : String.valueOf(idEntidad))
                .param("detalle", json.writeValueAsString(detalle == null ? Map.of() : detalle))
                .update();
    }

    @Override
    public List<EventoAuditoria> ultimos(int limite) {
        return jdbc.sql("""
                SELECT id_evento, fecha_hora, actor_tipo, actor_id, accion, entidad, id_entidad, detalle::text AS detalle, hash_actual
                  FROM auditoria.evento ORDER BY id_evento DESC LIMIT :limite
                """)
                .param("limite", Math.min(Math.max(limite, 1), 500))
                .query((rs, i) -> new EventoAuditoria(rs.getLong("id_evento"), Jdbc.fechaHora(rs, "fecha_hora", reloj.zona()),
                        rs.getString("actor_tipo"), rs.getString("actor_id"), rs.getString("accion"), rs.getString("entidad"),
                        rs.getString("id_entidad"), rs.getString("detalle"), rs.getString("hash_actual")))
                .list();
    }

    @Override
    public boolean cadenaIntegra() {
        var filas = jdbc.sql("""
                SELECT id_evento, fecha_hora::text AS fh, actor_tipo, actor_id, accion, entidad, id_entidad, detalle::text AS detalle,
                       hash_anterior, hash_actual
                  FROM auditoria.evento ORDER BY id_evento
                """).query().listOfRows();
        String previo = "0".repeat(64);
        for (Map<String, Object> f : filas) {
            if (!previo.equals(f.get("hash_anterior"))) {
                return false;
            }
            String material = f.get("hash_anterior") + "|" + f.get("id_evento") + "|" + f.get("fh") + "|" + f.get("actor_tipo") + "|"
                    + nulo(f.get("actor_id")) + "|" + f.get("accion") + "|" + f.get("entidad") + "|" + nulo(f.get("id_entidad")) + "|"
                    + f.get("detalle");
            if (!sha256(material).equals(f.get("hash_actual"))) {
                return false;
            }
            previo = (String) f.get("hash_actual");
        }
        return true;
    }

    private static String nulo(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
