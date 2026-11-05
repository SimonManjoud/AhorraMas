package ve.impulsatech.ahorramas.backoffice.adaptador.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.backoffice.aplicacion.puerto.salida.RepositorioBackoffice;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;

/** Adaptador PostgreSQL del esquema {@code backoffice}. */
@Repository
class PersistenciaBackofficeJdbc implements RepositorioBackoffice {

    private final JdbcClient jdbc;
    private final Reloj reloj;

    PersistenciaBackofficeJdbc(JdbcClient jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public Optional<Operador> operadorPorCorreo(String correo) {
        return jdbc.sql("SELECT * FROM backoffice.operador WHERE lower(correo_corporativo) = :c").param("c", correo)
                .query((rs, i) -> new Operador(rs.getLong("id_operador"), rs.getString("primer_nombre") + " " + rs.getString("primer_apellido"),
                        rs.getString("correo_corporativo"), rs.getString("rol"), rs.getString("estatus"), rs.getString("clave_hash")))
                .optional();
    }

    @Override
    public void asegurarOperador(String nombre, String apellido, String correo, String rol, String claveHash) {
        jdbc.sql("""
                INSERT INTO backoffice.operador (primer_nombre, primer_apellido, correo_corporativo, rol, estatus, clave_hash)
                VALUES (:n, :a, :c, :r, 'activo', :h) ON CONFLICT (correo_corporativo) DO NOTHING
                """).param("n", nombre).param("a", apellido).param("c", correo).param("r", rol).param("h", claveHash).update();
    }

    @Override
    public Optional<Long> abrirCaso(Long idUsuario, Long idPago, String tipo, String prioridad, String descripcion, String clave, OffsetDateTime ahora) {
        return jdbc.sql("""
                INSERT INTO backoffice.caso_soporte (id_usuario, id_pago, fecha_apertura, tipo, prioridad, estatus, descripcion, clave_origen)
                VALUES (:u, :p, :f, :t, :pr, 'abierto', :d, :k) ON CONFLICT (clave_origen) DO NOTHING RETURNING id_caso
                """).param("u", idUsuario).param("p", idPago).param("f", ahora).param("t", tipo).param("pr", prioridad).param("d", descripcion)
                .param("k", clave).query(Long.class).optional();
    }

    @Override
    public List<Caso> casos(String estatus, int limite) {
        return jdbc.sql("SELECT * FROM backoffice.caso_soporte WHERE (CAST(:e AS text) IS NULL OR estatus = :e) ORDER BY id_caso DESC LIMIT :l")
                .param("e", estatus).param("l", limite).query(this::mapCaso).list();
    }

    @Override
    public Optional<Caso> caso(long id) {
        return jdbc.sql("SELECT * FROM backoffice.caso_soporte WHERE id_caso = :id").param("id", id).query(this::mapCaso).optional();
    }

    @Override
    public void actualizarCaso(long id, String estatus, String resolucion, long idOperador, OffsetDateTime cierre) {
        jdbc.sql("""
                UPDATE backoffice.caso_soporte SET estatus = :e, resolucion = COALESCE(:r, resolucion), id_operador = :o, fecha_cierre = :c
                 WHERE id_caso = :id
                """).param("e", estatus).param("r", resolucion).param("o", idOperador).param("c", cierre).param("id", id).update();
    }

    @Override
    public Optional<Long> crearAlerta(long idUsuario, Long idPago, String regla, String nivel, String clave, OffsetDateTime ahora) {
        return jdbc.sql("""
                INSERT INTO backoffice.alerta_aml (id_usuario, id_pago, fecha_hora, regla_disparada, nivel_riesgo, estatus, clave_origen)
                VALUES (:u, :p, :f, :r, :n, 'nueva', :k) ON CONFLICT (clave_origen) DO NOTHING RETURNING id_alerta
                """).param("u", idUsuario).param("p", idPago).param("f", ahora).param("r", regla).param("n", nivel).param("k", clave)
                .query(Long.class).optional();
    }

    @Override
    public List<Alerta> alertas(String estatus, int limite) {
        return jdbc.sql("SELECT * FROM backoffice.alerta_aml WHERE (CAST(:e AS text) IS NULL OR estatus = :e) ORDER BY id_alerta DESC LIMIT :l")
                .param("e", estatus).param("l", limite).query(this::mapAlerta).list();
    }

    @Override
    public Optional<Alerta> alerta(long id) {
        return jdbc.sql("SELECT * FROM backoffice.alerta_aml WHERE id_alerta = :id").param("id", id).query(this::mapAlerta).optional();
    }

    @Override
    public void revisarAlerta(long id, String estatus, String justificacion, long idOperador, OffsetDateTime ahora) {
        jdbc.sql("""
                UPDATE backoffice.alerta_aml SET estatus = :e, justificacion = :j, id_operador = :o, fecha_revision = :f WHERE id_alerta = :id
                """).param("e", estatus).param("j", justificacion).param("o", idOperador).param("f", ahora).param("id", id).update();
    }

    @Override
    public long crearReporte(Long idAlerta, long idOperador, String tipo, String destinatario, String contenido, OffsetDateTime ahora) {
        return jdbc.sql("""
                INSERT INTO backoffice.reporte_regulatorio (id_alerta, id_operador, tipo, destinatario, fecha_generacion, estatus, contenido)
                VALUES (:a, :o, :t, :d, :f, 'borrador', CAST(:c AS jsonb)) RETURNING id_reporte_reg
                """).param("a", idAlerta).param("o", idOperador).param("t", tipo).param("d", destinatario).param("f", ahora).param("c", contenido)
                .query(Long.class).single();
    }

    @Override
    public List<Reporte> reportes(int limite) {
        return jdbc.sql("SELECT *, contenido::text AS contenido_txt FROM backoffice.reporte_regulatorio ORDER BY id_reporte_reg DESC LIMIT :l")
                .param("l", limite).query(this::mapReporte).list();
    }

    @Override
    public Optional<Reporte> reporte(long id) {
        return jdbc.sql("SELECT *, contenido::text AS contenido_txt FROM backoffice.reporte_regulatorio WHERE id_reporte_reg = :id")
                .param("id", id).query(this::mapReporte).optional();
    }

    @Override
    public void marcarEnviado(long id, String acuse, OffsetDateTime ahora) {
        jdbc.sql("UPDATE backoffice.reporte_regulatorio SET estatus = 'enviado', numero_acuse = :a, fecha_envio = :f WHERE id_reporte_reg = :id")
                .param("a", acuse).param("f", ahora).param("id", id).update();
    }

    private Caso mapCaso(ResultSet rs, int i) throws SQLException {
        var z = reloj.zona();
        return new Caso(rs.getLong("id_caso"), Jdbc.largoNulable(rs, "id_usuario"), Jdbc.largoNulable(rs, "id_pago"), Jdbc.largoNulable(rs, "id_operador"),
                Jdbc.fechaHora(rs, "fecha_apertura", z), Jdbc.fechaHora(rs, "fecha_cierre", z), rs.getString("tipo"), rs.getString("prioridad"),
                rs.getString("estatus"), rs.getString("descripcion"), rs.getString("resolucion"));
    }

    private Alerta mapAlerta(ResultSet rs, int i) throws SQLException {
        var z = reloj.zona();
        return new Alerta(rs.getLong("id_alerta"), rs.getLong("id_usuario"), Jdbc.largoNulable(rs, "id_pago"), Jdbc.largoNulable(rs, "id_operador"),
                Jdbc.fechaHora(rs, "fecha_hora", z), rs.getString("regla_disparada"), rs.getString("nivel_riesgo"), rs.getString("estatus"),
                Jdbc.fechaHora(rs, "fecha_revision", z), rs.getString("justificacion"));
    }

    private Reporte mapReporte(ResultSet rs, int i) throws SQLException {
        var z = reloj.zona();
        return new Reporte(rs.getLong("id_reporte_reg"), Jdbc.largoNulable(rs, "id_alerta"), rs.getLong("id_operador"), rs.getString("tipo"),
                rs.getString("destinatario"), Jdbc.fechaHora(rs, "fecha_generacion", z), Jdbc.fechaHora(rs, "fecha_envio", z), rs.getString("estatus"),
                rs.getString("numero_acuse"), rs.getString("contenido_txt"));
    }
}
