package ve.impulsatech.ahorramas.puntos.adaptador.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;
import ve.impulsatech.ahorramas.puntos.aplicacion.puerto.salida.RepositorioPuntos;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles.Nivel;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles.Recompensa;
import ve.impulsatech.ahorramas.puntos.dominio.TransaccionPuntos;

/** Adaptador PostgreSQL del ledger de puntos (append-only; partida doble verificada por trigger diferido). */
@Repository
class PersistenciaPuntosJdbc implements RepositorioPuntos {

    private final JdbcClient jdbc;
    private final Reloj reloj;

    PersistenciaPuntosJdbc(JdbcClient jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public long cuentaUsuario(long idUsuario, OffsetDateTime ahora) {
        return cuenta("usuario", "id_usuario", idUsuario, ahora);
    }

    @Override
    public long cuentaFondeoBanco(long idInstitucion, OffsetDateTime ahora) {
        return cuenta("fondeo_banco", "id_institucion", idInstitucion, ahora);
    }

    @Override
    public long cuentaComercio(long idComercio, OffsetDateTime ahora) {
        return cuenta("comercio", "id_comercio", idComercio, ahora);
    }

    private long cuenta(String tipo, String columna, long dueno, OffsetDateTime ahora) {
        jdbc.sql("INSERT INTO puntos.cuenta_puntos (tipo, fecha_apertura, " + columna + ") VALUES (:t, :f, :d) ON CONFLICT DO NOTHING")
                .param("t", tipo).param("f", ahora).param("d", dueno).update();
        return jdbc.sql("SELECT id_cuenta_puntos FROM puntos.cuenta_puntos WHERE " + columna + " = :d").param("d", dueno).query(Long.class).single();
    }

    @Override
    public long cuentaSistema() {
        return jdbc.sql("SELECT id_cuenta_puntos FROM puntos.cuenta_puntos WHERE tipo = 'sistema'").query(Long.class).single();
    }

    @Override
    public boolean existeClave(String clave) {
        return jdbc.sql("SELECT COUNT(*) FROM puntos.transaccion_puntos WHERE clave_origen = :c").param("c", clave).query(Long.class).single() > 0;
    }

    @Override
    public long registrar(TransaccionPuntos t) {
        long id = jdbc.sql("""
                INSERT INTO puntos.transaccion_puntos (fecha_hora, concepto, descripcion, clave_origen) VALUES (:f, :c, :d, :k) RETURNING id_transaccion
                """).param("f", t.fechaHora()).param("c", t.concepto())
                .param("d", t.descripcion() != null && t.descripcion().length() > 200 ? t.descripcion().substring(0, 200) : t.descripcion())
                .param("k", t.claveOrigen()).query(Long.class).single();
        int linea = 1;
        for (TransaccionPuntos.Asiento a : t.asientos()) {
            jdbc.sql("""
                    INSERT INTO puntos.asiento_puntos (id_transaccion, linea, id_cuenta_puntos, naturaleza, puntos) VALUES (:t, :l, :c, :n, :p)
                    """).param("t", id).param("l", linea++).param("c", a.idCuentaPuntos()).param("n", a.naturaleza()).param("p", a.puntos()).update();
        }
        return id;
    }

    @Override
    public long saldo(long cuenta) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN naturaleza = 'credito' THEN puntos ELSE -puntos END), 0) FROM puntos.asiento_puntos
                 WHERE id_cuenta_puntos = :c
                """).param("c", cuenta).query(Long.class).single();
    }

    @Override
    public long totalCreditos(long cuenta) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(a.puntos), 0) FROM puntos.asiento_puntos a JOIN puntos.transaccion_puntos t ON t.id_transaccion = a.id_transaccion
                 WHERE a.id_cuenta_puntos = :c AND a.naturaleza = 'credito' AND t.concepto <> 'reverso'
                """).param("c", cuenta).query(Long.class).single();
    }

    @Override
    public List<Movimiento> movimientos(long cuenta, int limite) {
        return jdbc.sql("""
                SELECT t.id_transaccion, t.fecha_hora, t.concepto, t.descripcion,
                       SUM(CASE WHEN a.naturaleza = 'credito' THEN a.puntos ELSE -a.puntos END) AS puntos
                  FROM puntos.transaccion_puntos t JOIN puntos.asiento_puntos a ON a.id_transaccion = t.id_transaccion
                 WHERE a.id_cuenta_puntos = :c
                 GROUP BY t.id_transaccion ORDER BY t.fecha_hora DESC, t.id_transaccion DESC LIMIT :l
                """).param("c", cuenta).param("l", limite)
                .query((rs, i) -> new Movimiento(rs.getLong("id_transaccion"), Jdbc.fechaHora(rs, "fecha_hora", reloj.zona()),
                        rs.getString("concepto"), rs.getString("descripcion"), rs.getLong("puntos")))
                .list();
    }

    @Override
    public List<Nivel> niveles() {
        return jdbc.sql("SELECT * FROM puntos.nivel_fidelidad ORDER BY orden").query(this::mapNivel).list();
    }

    @Override
    public Optional<NivelVigente> nivelVigente(long idUsuario) {
        return jdbc.sql("""
                SELECT n.*, h.fecha_inicio FROM puntos.historial_nivel h JOIN puntos.nivel_fidelidad n ON n.id_nivel = h.id_nivel
                 WHERE h.id_usuario = :u AND h.fecha_fin IS NULL
                """).param("u", idUsuario)
                .query((rs, i) -> new NivelVigente(mapNivel(rs, i), Jdbc.fechaHora(rs, "fecha_inicio", reloj.zona()))).optional();
    }

    @Override
    public void cambiarNivel(long idUsuario, int idNivel, OffsetDateTime ahora) {
        jdbc.sql("UPDATE puntos.historial_nivel SET fecha_fin = :f WHERE id_usuario = :u AND fecha_fin IS NULL AND fecha_inicio < :f")
                .param("u", idUsuario).param("f", ahora).update();
        jdbc.sql("INSERT INTO puntos.historial_nivel (id_usuario, id_nivel, fecha_inicio) VALUES (:u, :n, :f) ON CONFLICT DO NOTHING")
                .param("u", idUsuario).param("n", idNivel).param("f", ahora).update();
    }

    private static final String SQL_RECOMPENSA = """
            SELECT r.*, c.razon_social, n.id_nivel, n.nombre AS nivel_nombre, n.puntos_minimos, n.orden
              FROM puntos.recompensa r JOIN puntos.comercio_aliado c ON c.id_comercio = r.id_comercio
              JOIN puntos.nivel_fidelidad n ON n.id_nivel = r.id_nivel_minimo
            """;

    @Override
    public List<Recompensa> recompensas() {
        return jdbc.sql(SQL_RECOMPENSA + " WHERE c.estatus = 'activo' ORDER BY r.costo_puntos").query(this::mapRecompensa).list();
    }

    @Override
    public Optional<Recompensa> recompensaParaActualizar(long id) {
        return jdbc.sql(SQL_RECOMPENSA + " WHERE r.id_recompensa = :id FOR UPDATE OF r").param("id", id).query(this::mapRecompensa).optional();
    }

    @Override
    public void descontarStock(long id) {
        jdbc.sql("UPDATE puntos.recompensa SET stock_disponible = stock_disponible - 1 WHERE id_recompensa = :id").param("id", id).update();
    }

    @Override
    public long insertarCanje(long idRecompensa, long idUsuario, long idTransaccion, long puntos, String codigo, OffsetDateTime ahora) {
        return jdbc.sql("""
                INSERT INTO puntos.canje (id_recompensa, id_usuario, id_transaccion, fecha_hora, puntos_canjeados, codigo_canje, estatus)
                VALUES (:r, :u, :t, :f, :p, :c, 'emitido') RETURNING id_canje
                """).param("r", idRecompensa).param("u", idUsuario).param("t", idTransaccion).param("f", ahora).param("p", puntos)
                .param("c", codigo).query(Long.class).single();
    }

    @Override
    public List<Canje> canjes(long idUsuario) {
        return jdbc.sql("""
                SELECT c.*, r.descripcion FROM puntos.canje c JOIN puntos.recompensa r ON r.id_recompensa = c.id_recompensa
                 WHERE c.id_usuario = :u ORDER BY c.fecha_hora DESC
                """).param("u", idUsuario)
                .query((rs, i) -> new Canje(rs.getLong("id_canje"), rs.getString("descripcion"), rs.getString("codigo_canje"),
                        rs.getLong("puntos_canjeados"), rs.getString("estatus"), Jdbc.fechaHora(rs, "fecha_hora", reloj.zona())))
                .list();
    }

    private Nivel mapNivel(ResultSet rs, int i) throws SQLException {
        return new Nivel(rs.getInt("id_nivel"), rs.getString(rs.findColumn("nombre")), rs.getLong("puntos_minimos"), rs.getInt("orden"));
    }

    private Recompensa mapRecompensa(ResultSet rs, int i) throws SQLException {
        Nivel n = new Nivel(rs.getInt("id_nivel"), rs.getString("nivel_nombre"), rs.getLong("puntos_minimos"), rs.getInt("orden"));
        return new Recompensa(rs.getLong("id_recompensa"), rs.getString("descripcion"), rs.getLong("costo_puntos"), rs.getString("razon_social"),
                rs.getLong("id_comercio"), n, Jdbc.fecha(rs, "fecha_inicio"), Jdbc.fecha(rs, "fecha_fin"), Jdbc.enteroNulable(rs, "stock_disponible"));
    }
}
