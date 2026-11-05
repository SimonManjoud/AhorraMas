package ve.impulsatech.ahorramas.consolidacion.adaptador.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi.Alianza;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.*;
import ve.impulsatech.ahorramas.consolidacion.dominio.Consentimiento;
import ve.impulsatech.ahorramas.consolidacion.dominio.CuentaConSaldo;
import ve.impulsatech.ahorramas.consolidacion.dominio.Institucion;

/** Adaptador PostgreSQL del esquema {@code consolidacion}. */
@Repository
class PersistenciaConsolidacionJdbc implements Repositorio {

    private static final String SQL_CUENTAS = """
            SELECT c.id_cuenta, c.id_usuario, c.id_consentimiento, c.id_institucion, i.codigo_integracion, i.nombre, i.tipo AS tipo_inst,
                   c.ref_cuenta_externa, c.ultimos_4, c.tipo, m.codigo AS moneda, c.alias_usuario, c.solo_lectura, c.estatus,
                   s.saldo_cts, s.fecha_hora_sincronizacion
              FROM consolidacion.cuenta_vinculada c
              JOIN consolidacion.institucion_financiera i ON i.id_institucion = c.id_institucion
              JOIN consolidacion.moneda m ON m.id_moneda = c.id_moneda
              LEFT JOIN LATERAL (SELECT saldo_cts, fecha_hora_sincronizacion FROM consolidacion.saldo_cuenta sc
                                  WHERE sc.id_cuenta = c.id_cuenta ORDER BY fecha_hora_sincronizacion DESC, id_saldo DESC LIMIT 1) s ON TRUE
            """;

    private final JdbcClient jdbc;
    private final Reloj reloj;

    PersistenciaConsolidacionJdbc(JdbcClient jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public List<Institucion> instituciones() {
        return jdbc.sql("SELECT * FROM consolidacion.institucion_financiera ORDER BY tipo, nombre").query(this::mapInstitucion).list();
    }

    @Override
    public Optional<Institucion> institucion(long id) {
        return jdbc.sql("SELECT * FROM consolidacion.institucion_financiera WHERE id_institucion = :id").param("id", id)
                .query(this::mapInstitucion).optional();
    }

    @Override
    public Consentimiento guardar(Consentimiento c) {
        if (c.idConsentimiento() == null) {
            GeneratedKeyHolder kh = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO consolidacion.consentimiento (id_usuario, id_institucion, fecha_otorgamiento, fecha_vencimiento, fecha_revocacion,
                        estatus, permite_lectura, permite_debito_tdc, ref_token_boveda, motivo_revocacion, estado_oauth)
                    VALUES (:u, :i, :fo, :fv, :fr, :e, :pl, :pd, :ref, :mot, :st)
                    """).params(parametros(c)).update(kh, "id_consentimiento");
            return consentimiento(kh.getKey().longValue()).orElseThrow();
        }
        jdbc.sql("""
                UPDATE consolidacion.consentimiento SET fecha_otorgamiento = :fo, fecha_vencimiento = :fv, fecha_revocacion = :fr, estatus = :e,
                    permite_lectura = :pl, permite_debito_tdc = :pd, ref_token_boveda = :ref, motivo_revocacion = :mot, estado_oauth = :st
                 WHERE id_consentimiento = :id
                """).params(parametros(c)).param("id", c.idConsentimiento()).update();
        return c;
    }

    private static java.util.Map<String, Object> parametros(Consentimiento c) {
        var m = new java.util.HashMap<String, Object>();
        m.put("u", c.idUsuario());
        m.put("i", c.idInstitucion());
        m.put("fo", c.fechaOtorgamiento());
        m.put("fv", c.fechaVencimiento());
        m.put("fr", c.fechaRevocacion());
        m.put("e", c.estatus());
        m.put("pl", c.permiteLectura());
        m.put("pd", c.permiteDebitoTdc());
        m.put("ref", c.refTokenBoveda());
        m.put("mot", c.motivoRevocacion());
        m.put("st", c.estadoOauth());
        return m;
    }

    @Override
    public Optional<Consentimiento> consentimiento(long id) {
        return jdbc.sql("SELECT * FROM consolidacion.consentimiento WHERE id_consentimiento = :id").param("id", id)
                .query(this::consentimientoFila).optional();
    }

    @Override
    public Optional<Consentimiento> consentimientoPorEstado(String estado) {
        return jdbc.sql("SELECT * FROM consolidacion.consentimiento WHERE estado_oauth = :s").param("s", estado)
                .query(this::consentimientoFila).optional();
    }

    @Override
    public List<Consentimiento> consentimientos(long idUsuario) {
        return jdbc.sql("SELECT * FROM consolidacion.consentimiento WHERE id_usuario = :u ORDER BY id_consentimiento DESC")
                .param("u", idUsuario).query(this::consentimientoFila).list();
    }

    @Override
    public List<Consentimiento> consentimientosActivos() {
        return jdbc.sql("SELECT * FROM consolidacion.consentimiento WHERE estatus = 'activo' AND fecha_vencimiento > now()")
                .query(this::consentimientoFila).list();
    }

    @Override
    public void vencerPendientes(long idUsuario, long idInstitucion) {
        jdbc.sql("""
                UPDATE consolidacion.consentimiento SET estatus = 'vencido', estado_oauth = NULL
                 WHERE id_usuario = :u AND id_institucion = :i AND estatus = 'pendiente'
                """).param("u", idUsuario).param("i", idInstitucion).update();
    }

    @Override
    public long guardarCuenta(long idUsuario, long idConsentimiento, Institucion inst, CuentaExterna c, OffsetDateTime ahora) {
        String ult4 = c.ultimos4() != null && c.ultimos4().matches("\\d{4}") ? c.ultimos4() : "0000";
        return jdbc.sql("""
                INSERT INTO consolidacion.cuenta_vinculada (id_consentimiento, id_usuario, id_institucion, id_moneda, ref_cuenta_externa,
                    ultimos_4, tipo, solo_lectura, estatus, fecha_vinculacion)
                VALUES (:c, :u, :i, (SELECT id_moneda FROM consolidacion.moneda WHERE codigo = :mon), :ref, :u4, :tipo, :sl, 'activa', :f)
                ON CONFLICT (id_usuario, id_institucion, ref_cuenta_externa)
                DO UPDATE SET id_consentimiento = EXCLUDED.id_consentimiento, estatus = 'activa', ultimos_4 = EXCLUDED.ultimos_4,
                              solo_lectura = EXCLUDED.solo_lectura
                RETURNING id_cuenta
                """)
                .param("c", idConsentimiento).param("u", idUsuario).param("i", inst.idInstitucion())
                .param("mon", Moneda.de(c.moneda()).name()).param("ref", c.refCuenta()).param("u4", ult4)
                .param("tipo", c.tipo()).param("sl", CuentaConSaldo.soloLecturaPara(inst.tipo(), c.tipo())).param("f", ahora)
                .query(Long.class).single();
    }

    @Override
    public void inactivarCuentas(long idConsentimiento) {
        jdbc.sql("UPDATE consolidacion.cuenta_vinculada SET estatus = 'inactiva' WHERE id_consentimiento = :c")
                .param("c", idConsentimiento).update();
    }

    @Override
    public void registrarSaldo(long idCuenta, long saldoCts, Long disponibleCts, OffsetDateTime fecha) {
        jdbc.sql("""
                INSERT INTO consolidacion.saldo_cuenta (id_cuenta, fecha_hora_sincronizacion, saldo_cts, saldo_disponible_cts)
                VALUES (:c, :f, :s, :d)
                """).param("c", idCuenta).param("f", fecha).param("s", saldoCts).param("d", disponibleCts).update();
    }

    @Override
    public Optional<Long> registrarMovimiento(long idCuenta, MovimientoExterno m) {
        return jdbc.sql("""
                INSERT INTO consolidacion.movimiento (id_cuenta, ref_movimiento_externo, fecha_hora, naturaleza, clasificacion, monto_cts, descripcion)
                VALUES (:c, :ref, :f, :n, :cl, :m, :d)
                ON CONFLICT (id_cuenta, ref_movimiento_externo) DO NOTHING
                RETURNING id_movimiento
                """)
                .param("c", idCuenta).param("ref", m.refMovimiento()).param("f", m.fechaHora()).param("n", m.naturaleza())
                .param("cl", clasificacion(m)).param("m", m.montoCts())
                .param("d", m.descripcion() == null ? null : m.descripcion().length() > 200 ? m.descripcion().substring(0, 200) : m.descripcion())
                .query(Long.class).optional();
    }

    private static String clasificacion(MovimientoExterno m) {
        String s = m.clasificacionSugerida();
        if (s != null && List.of("ingreso", "gasto", "transf_propia", "pago_tdc").contains(s)) {
            return s;
        }
        return "credito".equals(m.naturaleza()) ? "ingreso" : "gasto";
    }

    @Override
    public List<CuentaConSaldo> cuentas(long idUsuario) {
        return jdbc.sql(SQL_CUENTAS + " WHERE c.id_usuario = :u ORDER BY i.nombre, c.id_cuenta").param("u", idUsuario).query(this::mapCuenta).list();
    }

    @Override
    public Optional<CuentaConSaldo> cuenta(long idCuenta) {
        return jdbc.sql(SQL_CUENTAS + " WHERE c.id_cuenta = :id").param("id", idCuenta).query(this::mapCuenta).optional();
    }

    @Override
    public void cambiarAlias(long idCuenta, String alias) {
        jdbc.sql("UPDATE consolidacion.cuenta_vinculada SET alias_usuario = :a WHERE id_cuenta = :id").param("a", alias).param("id", idCuenta).update();
    }

    @Override
    public List<MovimientoGuardado> movimientos(long idCuenta, int limite) {
        return jdbc.sql("""
                SELECT mv.*, m.codigo AS moneda FROM consolidacion.movimiento mv
                  JOIN consolidacion.cuenta_vinculada c ON c.id_cuenta = mv.id_cuenta
                  JOIN consolidacion.moneda m ON m.id_moneda = c.id_moneda
                 WHERE mv.id_cuenta = :c ORDER BY mv.fecha_hora DESC LIMIT :l
                """).param("c", idCuenta).param("l", limite).query(this::movimiento).list();
    }

    @Override
    public List<MovimientoGuardado> movimientosUsuarioDesde(long idUsuario, OffsetDateTime desde) {
        return jdbc.sql("""
                SELECT mv.*, m.codigo AS moneda FROM consolidacion.movimiento mv
                  JOIN consolidacion.cuenta_vinculada c ON c.id_cuenta = mv.id_cuenta
                  JOIN consolidacion.moneda m ON m.id_moneda = c.id_moneda
                 WHERE c.id_usuario = :u AND c.estatus = 'activa' AND mv.fecha_hora >= :d ORDER BY mv.fecha_hora
                """).param("u", idUsuario).param("d", desde).query(this::movimiento).list();
    }

    @Override
    public Optional<TasaBcv> tasaGuardada(LocalDate fecha) {
        return jdbc.sql("""
                SELECT fecha_vigencia, valor_bs_x10000 FROM consolidacion.tasa_cambio t JOIN consolidacion.moneda m ON m.id_moneda = t.id_moneda
                 WHERE m.codigo = 'USD' AND fecha_vigencia = :f
                """).param("f", fecha).query(this::tasa).optional();
    }

    @Override
    public Optional<TasaBcv> ultimaTasaHasta(LocalDate fecha) {
        return jdbc.sql("""
                SELECT fecha_vigencia, valor_bs_x10000 FROM consolidacion.tasa_cambio t JOIN consolidacion.moneda m ON m.id_moneda = t.id_moneda
                 WHERE m.codigo = 'USD' AND fecha_vigencia <= :f ORDER BY fecha_vigencia DESC LIMIT 1
                """).param("f", fecha).query(this::tasa).optional();
    }

    @Override
    public void guardarTasa(TasaBcv t, OffsetDateTime obtenida) {
        jdbc.sql("""
                INSERT INTO consolidacion.tasa_cambio (id_moneda, fecha_vigencia, valor_bs_x10000, fecha_hora_obtencion)
                VALUES ((SELECT id_moneda FROM consolidacion.moneda WHERE codigo = 'USD'), :f, :v, :o)
                ON CONFLICT (id_moneda, fecha_vigencia) DO NOTHING
                """).param("f", t.fecha()).param("v", t.valorBsX10000()).param("o", obtenida).update();
    }

    @Override
    public List<TasaBcv> tasasGuardadas(LocalDate desde, LocalDate hasta) {
        return jdbc.sql("""
                SELECT fecha_vigencia, valor_bs_x10000 FROM consolidacion.tasa_cambio t JOIN consolidacion.moneda m ON m.id_moneda = t.id_moneda
                 WHERE m.codigo = 'USD' AND fecha_vigencia BETWEEN :d AND :h ORDER BY fecha_vigencia
                """).param("d", desde).param("h", hasta).query(this::tasa).list();
    }

    @Override
    public Optional<Integer> inflacionGuardada(int anio, int mes) {
        return jdbc.sql("SELECT variacion_mensual_pb FROM consolidacion.indicador_inflacion WHERE anio = :a AND mes = :m")
                .param("a", anio).param("m", mes).query(Integer.class).optional();
    }

    @Override
    public void guardarInflacion(int anio, int mes, int pb, LocalDate publicacion) {
        jdbc.sql("""
                INSERT INTO consolidacion.indicador_inflacion (anio, mes, variacion_mensual_pb, fecha_publicacion) VALUES (:a, :m, :pb, :f)
                ON CONFLICT (anio, mes) DO NOTHING
                """).param("a", anio).param("m", mes).param("pb", pb).param("f", publicacion).update();
    }

    @Override
    public Optional<Alianza> alianzaVigente(long idInstitucion, LocalDate hoy) {
        return jdbc.sql("""
                SELECT id_institucion, fee_cliente_puntual_cts, puntos_por_pago_puntual FROM consolidacion.alianza_bancaria
                 WHERE id_institucion = :i AND fecha_inicio <= :h AND (fecha_fin IS NULL OR fecha_fin > :h)
                """).param("i", idInstitucion).param("h", hoy)
                .query((rs, n) -> new Alianza(rs.getLong(1), rs.getLong(2), rs.getInt(3))).optional();
    }

    private Institucion mapInstitucion(ResultSet rs, int i) throws SQLException {
        return new Institucion(rs.getLong("id_institucion"), rs.getString("codigo_integracion"), rs.getString("nombre"), rs.getString("tipo"),
                rs.getString("estatus_integracion"), rs.getString("codigo_sudeban"), rs.getString("tipo_plataforma"));
    }

    private Consentimiento consentimientoFila(ResultSet rs, int i) throws SQLException {
        var z = reloj.zona();
        return new Consentimiento(rs.getLong("id_consentimiento"), rs.getLong("id_usuario"), rs.getLong("id_institucion"),
                Jdbc.fechaHora(rs, "fecha_otorgamiento", z), Jdbc.fechaHora(rs, "fecha_vencimiento", z), Jdbc.fechaHora(rs, "fecha_revocacion", z),
                rs.getString("estatus"), rs.getBoolean("permite_lectura"), rs.getBoolean("permite_debito_tdc"), rs.getString("ref_token_boveda"),
                rs.getString("motivo_revocacion"), rs.getString("estado_oauth"));
    }

    private CuentaConSaldo mapCuenta(ResultSet rs, int i) throws SQLException {
        return new CuentaConSaldo(rs.getLong("id_cuenta"), rs.getLong("id_usuario"), rs.getLong("id_consentimiento"), rs.getLong("id_institucion"),
                rs.getString("codigo_integracion"), rs.getString("nombre"), rs.getString("tipo_inst"), rs.getString("ref_cuenta_externa"),
                rs.getString("ultimos_4"), rs.getString("tipo"), Moneda.de(rs.getString("moneda")), rs.getString("alias_usuario"),
                rs.getBoolean("solo_lectura"), rs.getString("estatus"), Jdbc.largoNulable(rs, "saldo_cts"),
                Jdbc.fechaHora(rs, "fecha_hora_sincronizacion", reloj.zona()));
    }

    private MovimientoGuardado movimiento(ResultSet rs, int i) throws SQLException {
        return new MovimientoGuardado(rs.getLong("id_movimiento"), rs.getLong("id_cuenta"), Jdbc.fechaHora(rs, "fecha_hora", reloj.zona()),
                rs.getString("naturaleza"), rs.getString("clasificacion"), rs.getLong("monto_cts"), rs.getString("moneda"), rs.getString("descripcion"));
    }

    private TasaBcv tasa(ResultSet rs, int i) throws SQLException {
        return new TasaBcv(Jdbc.fecha(rs, "fecha_vigencia"), rs.getLong("valor_bs_x10000"));
    }
}
