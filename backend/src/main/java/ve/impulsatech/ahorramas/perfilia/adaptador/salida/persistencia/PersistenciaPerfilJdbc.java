package ve.impulsatech.ahorramas.perfilia.adaptador.salida.persistencia;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;
import ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.salida.PuertosPerfil.*;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil.Rasgos;

/** Adaptador PostgreSQL del esquema {@code perfilia}. */
@Repository
class PersistenciaPerfilJdbc implements Repositorio {

    private final JdbcClient jdbc;
    private final Reloj reloj;

    PersistenciaPerfilJdbc(JdbcClient jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public VersionModelo versionActiva() {
        return jdbc.sql("SELECT id_version, version_reglas, version_modelo FROM perfilia.version_modelo_ia WHERE estatus = 'activa'")
                .query((rs, i) -> new VersionModelo(rs.getLong(1), rs.getString(2), rs.getString(3))).single();
    }

    @Override
    public long guardarEvaluacion(long idUsuario, long idVersion, Rasgos r, String perfil, OffsetDateTime ahora) {
        jdbc.sql("UPDATE perfilia.evaluacion_perfil SET fecha_fin = :f WHERE id_usuario = :u AND fecha_fin IS NULL AND fecha_inicio < :f")
                .param("f", ahora).param("u", idUsuario).update();
        return jdbc.sql("""
                INSERT INTO perfilia.evaluacion_perfil (id_usuario, id_version, fecha_inicio, objetivo_principal, horizonte_meses, tolerancia_riesgo,
                    rango_ingreso, volatilidad_saldo_pb, perfil_resultante)
                VALUES (:u, :v, :f, :o, :h, :t, :r, :vol, :p) RETURNING id_evaluacion
                """).param("u", idUsuario).param("v", idVersion).param("f", ahora).param("o", r.objetivoPrincipal()).param("h", r.horizonteMeses())
                .param("t", r.toleranciaRiesgo()).param("r", r.rangoIngreso()).param("vol", r.volatilidadSaldoPb()).param("p", perfil)
                .query(Long.class).single();
    }

    @Override
    public long guardarRecomendacion(long idEvaluacion, String explicacion, Map<String, Integer> distribucion, String reglaTipo, long reglaValor,
                                     OffsetDateTime ahora) {
        long id = jdbc.sql("""
                INSERT INTO perfilia.recomendacion_ia (id_evaluacion, fecha_hora, explicacion, estatus, regla_sugerida_tipo, regla_sugerida_valor)
                VALUES (:e, :f, :x, 'propuesta', :rt, :rv) RETURNING id_recomendacion
                """).param("e", idEvaluacion).param("f", ahora).param("x", explicacion).param("rt", reglaTipo).param("rv", reglaValor)
                .query(Long.class).single();
        distribucion.forEach((instrumento, pb) -> jdbc.sql("""
                INSERT INTO perfilia.distribucion_sugerida (id_recomendacion, id_instrumento, porcentaje_pb)
                VALUES (:r, (SELECT id_instrumento FROM perfilia.instrumento_ahorro WHERE nombre = :n), :pb)
                """).param("r", id).param("n", instrumento).param("pb", pb).update());
        return id;
    }

    private static final String SQL_RECOMENDACION = """
            SELECT r.*, e.id_usuario, e.perfil_resultante, v.version_reglas || ' / ' || v.version_modelo AS version
              FROM perfilia.recomendacion_ia r JOIN perfilia.evaluacion_perfil e ON e.id_evaluacion = r.id_evaluacion
              JOIN perfilia.version_modelo_ia v ON v.id_version = e.id_version
            """;

    @Override
    public Optional<Recomendacion> recomendacion(long id) {
        return jdbc.sql(SQL_RECOMENDACION + " WHERE r.id_recomendacion = :id").param("id", id).query(this::mapear).optional();
    }

    @Override
    public List<Recomendacion> recomendaciones(long idUsuario) {
        return jdbc.sql(SQL_RECOMENDACION + " WHERE e.id_usuario = :u ORDER BY r.fecha_hora DESC").param("u", idUsuario).query(this::mapear).list();
    }

    @Override
    public void decidir(long id, String estatus, Boolean entendio, OffsetDateTime ahora) {
        jdbc.sql("UPDATE perfilia.recomendacion_ia SET estatus = :e, entendio_explicacion = :x, fecha_decision = :f WHERE id_recomendacion = :id")
                .param("e", estatus).param("x", entendio).param("f", ahora).param("id", id).update();
    }

    private Recomendacion mapear(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        long id = rs.getLong("id_recomendacion");
        List<Distribucion> dist = jdbc.sql("""
                SELECT i.nombre, d.porcentaje_pb, i.nivel_riesgo FROM perfilia.distribucion_sugerida d
                  JOIN perfilia.instrumento_ahorro i ON i.id_instrumento = d.id_instrumento
                 WHERE d.id_recomendacion = :r ORDER BY d.porcentaje_pb DESC
                """).param("r", id).query((r2, j) -> new Distribucion(r2.getString(1), r2.getInt(2), r2.getString(3))).list();
        return new Recomendacion(id, rs.getLong("id_usuario"), rs.getString("perfil_resultante"), Jdbc.fechaHora(rs, "fecha_hora", reloj.zona()),
                rs.getString("explicacion"), rs.getString("estatus"), dist, rs.getString("regla_sugerida_tipo"),
                Jdbc.largoNulable(rs, "regla_sugerida_valor"), rs.getString("version"));
    }
}
