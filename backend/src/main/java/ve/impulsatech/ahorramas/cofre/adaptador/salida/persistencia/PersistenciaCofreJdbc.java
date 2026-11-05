package ve.impulsatech.ahorramas.cofre.adaptador.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.dominio.Aporte;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.Racha;
import ve.impulsatech.ahorramas.cofre.dominio.ReglaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.SelloCompromiso;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;

/** Adaptador PostgreSQL del esquema {@code cofre}. El ledger de aportes solo admite INSERT (trigger R-10). */
@Repository
class PersistenciaCofreJdbc implements RepositorioCofre {

    private final JdbcClient jdbc;
    private final Reloj reloj;

    PersistenciaCofreJdbc(JdbcClient jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    // ---------------------------------------------------------------- metas

    @Override
    public MetaAhorro guardarMeta(MetaAhorro m) {
        if (m.idMeta() == null) {
            GeneratedKeyHolder kh = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO cofre.meta_ahorro (id_usuario, nombre, categoria, monto_objetivo_usd_cts, fecha_objetivo, frecuencia_cuota,
                        fecha_creacion, estatus, fecha_cierre)
                    VALUES (:u, :n, :c, :m, :fo, :fr, :fc, :e, :fcie)
                    """).param("u", m.idUsuario()).param("n", m.nombre()).param("c", m.categoria()).param("m", m.montoObjetivoUsdCts())
                    .param("fo", m.fechaObjetivo()).param("fr", m.frecuenciaCuota()).param("fc", m.fechaCreacion())
                    .param("e", m.estatus()).param("fcie", m.fechaCierre())
                    .update(kh, "id_meta");
            return meta(kh.getKey().longValue()).orElseThrow();
        }
        jdbc.sql("UPDATE cofre.meta_ahorro SET estatus = :e, fecha_cierre = :f WHERE id_meta = :id")
                .param("e", m.estatus()).param("f", m.fechaCierre()).param("id", m.idMeta()).update();
        return m;
    }

    @Override
    public Optional<MetaAhorro> meta(long idMeta) {
        return jdbc.sql("SELECT * FROM cofre.meta_ahorro WHERE id_meta = :id").param("id", idMeta).query(this::mapMeta).optional();
    }

    @Override
    public Optional<MetaAhorro> metaParaActualizar(long idMeta) {
        return jdbc.sql("SELECT * FROM cofre.meta_ahorro WHERE id_meta = :id FOR UPDATE").param("id", idMeta).query(this::mapMeta).optional();
    }

    @Override
    public List<MetaAhorro> metas(long idUsuario) {
        return jdbc.sql("SELECT * FROM cofre.meta_ahorro WHERE id_usuario = :u ORDER BY (estatus = 'activa') DESC, fecha_objetivo, id_meta")
                .param("u", idUsuario).query(this::mapMeta).list();
    }

    @Override
    public List<MetaAhorro> metasActivasDeTodos() {
        return jdbc.sql("SELECT * FROM cofre.meta_ahorro WHERE estatus = 'activa'").query(this::mapMeta).list();
    }

    @Override
    public List<Long> usuariosConMetas() {
        return jdbc.sql("SELECT DISTINCT id_usuario FROM cofre.meta_ahorro").query(Long.class).list();
    }

    // ---------------------------------------------------------------- ledger de aportes

    @Override
    public Aporte insertarAporte(Aporte a) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO cofre.aporte (id_meta, id_cuenta, id_regla, id_cuota, fecha_hora, tipo, origen, monto_usd_cts, monto_bs_cts,
                    tasa_valor_bs_x10000, clave_origen)
                VALUES (:m, :c, :r, :q, :f, :t, :o, :usd, :bs, :tasa, :clave)
                """).param("m", a.idMeta()).param("c", a.idCuenta()).param("r", a.idRegla()).param("q", a.idCuota())
                .param("f", a.fechaHora()).param("t", a.tipo()).param("o", a.origen()).param("usd", a.montoUsdCts())
                .param("bs", a.montoBsCts()).param("tasa", a.tasaValorBsX10000()).param("clave", a.claveOrigen())
                .update(kh, "id_aporte");
        return new Aporte(kh.getKey().longValue(), a.idMeta(), a.idCuenta(), a.idRegla(), a.idCuota(), a.fechaHora(), a.tipo(), a.origen(),
                a.montoUsdCts(), a.montoBsCts(), a.tasaValorBsX10000(), a.claveOrigen());
    }

    @Override
    public boolean existeClaveOrigen(String clave) {
        return jdbc.sql("SELECT COUNT(*) FROM cofre.aporte WHERE clave_origen = :c").param("c", clave).query(Long.class).single() > 0;
    }

    @Override
    public List<Aporte> aportes(long idMeta) {
        return jdbc.sql("SELECT * FROM cofre.aporte WHERE id_meta = :m ORDER BY fecha_hora, id_aporte").param("m", idMeta)
                .query(this::mapAporte).list();
    }

    @Override
    public List<Aporte> aportesDeUsuario(long idUsuario) {
        return jdbc.sql("""
                SELECT a.* FROM cofre.aporte a JOIN cofre.meta_ahorro m ON m.id_meta = a.id_meta
                 WHERE m.id_usuario = :u ORDER BY a.fecha_hora, a.id_aporte
                """).param("u", idUsuario).query(this::mapAporte).list();
    }

    @Override
    public long acumulado(long idMeta) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN tipo = 'aporte' THEN monto_usd_cts ELSE -monto_usd_cts END), 0)
                  FROM cofre.aporte WHERE id_meta = :m
                """).param("m", idMeta).query(Long.class).single();
    }

    @Override
    public long aportadoEntre(long idMeta, OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(monto_usd_cts), 0) FROM cofre.aporte
                 WHERE id_meta = :m AND tipo = 'aporte' AND fecha_hora >= :d AND fecha_hora < :h
                """).param("m", idMeta).param("d", desde).param("h", hasta).query(Long.class).single();
    }

    @Override
    public boolean huboAporteDelUsuarioEntre(long idUsuario, OffsetDateTime desde, OffsetDateTime hasta, long excluir) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM cofre.aporte a JOIN cofre.meta_ahorro m ON m.id_meta = a.id_meta
                 WHERE m.id_usuario = :u AND a.tipo = 'aporte' AND a.fecha_hora >= :d AND a.fecha_hora < :h AND a.id_aporte <> :x
                """).param("u", idUsuario).param("d", desde).param("h", hasta).param("x", excluir).query(Long.class).single() > 0;
    }

    // ---------------------------------------------------------------- cuotas (Ruta del día)

    @Override
    public Optional<CuotaGuardada> cuota(long idMeta, LocalDate fecha) {
        return jdbc.sql("SELECT * FROM cofre.cuota_diaria WHERE id_meta = :m AND fecha = :f").param("m", idMeta).param("f", fecha)
                .query(this::mapCuota).optional();
    }

    @Override
    public CuotaGuardada insertarCuota(CuotaGuardada c) {
        jdbc.sql("""
                INSERT INTO cofre.cuota_diaria (id_meta, fecha, monto_sugerido_usd_cts, monto_sugerido_bs_cts, tasa_valor_bs_x10000,
                    periodos_restantes, estatus)
                VALUES (:m, :f, :usd, :bs, :t, :p, :e)
                ON CONFLICT (id_meta, fecha) DO NOTHING
                """).param("m", c.idMeta()).param("f", c.fecha()).param("usd", c.montoSugeridoUsdCts()).param("bs", c.montoSugeridoBsCts())
                .param("t", c.tasaValorBsX10000()).param("p", c.periodosRestantes()).param("e", c.estatus()).update();
        return cuota(c.idMeta(), c.fecha()).orElseThrow();
    }

    @Override
    public void actualizarEstatusCuota(long idCuota, String estatus) {
        jdbc.sql("UPDATE cofre.cuota_diaria SET estatus = :e WHERE id_cuota = :id").param("e", estatus).param("id", idCuota).update();
    }

    @Override
    public List<CuotaGuardada> cuotasPendientesHasta(LocalDate fecha) {
        return jdbc.sql("SELECT * FROM cofre.cuota_diaria WHERE estatus = 'pendiente' AND fecha <= :f").param("f", fecha)
                .query(this::mapCuota).list();
    }

    // ---------------------------------------------------------------- reglas

    @Override
    public ReglaAhorro guardarRegla(ReglaAhorro r) {
        if (r.idRegla() == null) {
            GeneratedKeyHolder kh = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO cofre.regla_ahorro (id_meta, id_recomendacion, tipo, fecha_aprobacion, fecha_inicio_vigencia, fecha_fin_vigencia,
                        estatus, porcentaje_pb, multiplo_redondeo_cts, monto_fijo_usd_cts)
                    VALUES (:m, :rec, :t, :fa, :fi, :ff, :e, :pct, :mul, :fijo)
                    """).param("m", r.idMeta()).param("rec", r.idRecomendacion()).param("t", r.tipo()).param("fa", r.fechaAprobacion())
                    .param("fi", r.fechaInicioVigencia()).param("ff", r.fechaFinVigencia()).param("e", r.estatus())
                    .param("pct", r.porcentajePb()).param("mul", r.multiploRedondeoCts()).param("fijo", r.montoFijoUsdCts())
                    .update(kh, "id_regla");
            return regla(kh.getKey().longValue()).orElseThrow();
        }
        jdbc.sql("UPDATE cofre.regla_ahorro SET estatus = :e, fecha_fin_vigencia = :ff WHERE id_regla = :id")
                .param("e", r.estatus()).param("ff", r.fechaFinVigencia()).param("id", r.idRegla()).update();
        return r;
    }

    @Override
    public Optional<ReglaAhorro> regla(long idRegla) {
        return jdbc.sql("SELECT * FROM cofre.regla_ahorro WHERE id_regla = :id").param("id", idRegla).query(this::mapRegla).optional();
    }

    @Override
    public List<ReglaAhorro> reglas(long idMeta) {
        return jdbc.sql("SELECT * FROM cofre.regla_ahorro WHERE id_meta = :m ORDER BY id_regla").param("m", idMeta).query(this::mapRegla).list();
    }

    @Override
    public List<ReglaActivaDeUsuario> reglasActivasDeUsuario(long idUsuario) {
        return reglasActivas("m.id_usuario = :v", idUsuario);
    }

    @Override
    public List<ReglaActivaDeUsuario> reglasActivasPorTipo(String tipo) {
        return reglasActivas("r.tipo = :v", tipo);
    }

    private List<ReglaActivaDeUsuario> reglasActivas(String filtro, Object valor) {
        return jdbc.sql("""
                SELECT r.*, m.id_usuario, m.nombre, m.categoria, m.monto_objetivo_usd_cts, m.fecha_objetivo, m.frecuencia_cuota,
                       m.fecha_creacion, m.estatus AS estatus_meta, m.fecha_cierre
                  FROM cofre.regla_ahorro r JOIN cofre.meta_ahorro m ON m.id_meta = r.id_meta
                 WHERE r.estatus = 'activa' AND m.estatus = 'activa' AND\s""" + filtro + " ORDER BY r.id_regla")
                .param("v", valor)
                .query((rs, i) -> new ReglaActivaDeUsuario(mapRegla(rs, i), new MetaAhorro(rs.getLong("id_meta"), rs.getLong("id_usuario"),
                        rs.getString("nombre"), rs.getString("categoria"), rs.getLong("monto_objetivo_usd_cts"), Jdbc.fecha(rs, "fecha_objetivo"),
                        rs.getString("frecuencia_cuota"), Jdbc.fechaHora(rs, "fecha_creacion", reloj.zona()), rs.getString("estatus_meta"),
                        Jdbc.fechaHora(rs, "fecha_cierre", reloj.zona()))))
                .list();
    }

    // ---------------------------------------------------------------- racha

    @Override
    public List<Racha.Tramo> tramosVigentes() {
        return jdbc.sql("SELECT dias_minimos, multiplicador_x100, dias_colchon_otorgados FROM cofre.multiplicador_racha WHERE fecha_fin IS NULL ORDER BY dias_minimos")
                .query((rs, i) -> new Racha.Tramo(rs.getInt(1), rs.getInt(2), rs.getInt(3))).list();
    }

    @Override
    public Optional<Racha> rachaActiva(long idUsuario) {
        return jdbc.sql("SELECT * FROM cofre.racha WHERE id_usuario = :u AND fecha_fin IS NULL").param("u", idUsuario)
                .query(this::mapRachaSinDias).optional().map(this::conDias);
    }

    @Override
    public Optional<Racha> rachaActivaParaActualizar(long idUsuario) {
        return jdbc.sql("SELECT * FROM cofre.racha WHERE id_usuario = :u AND fecha_fin IS NULL FOR UPDATE").param("u", idUsuario)
                .query(this::mapRachaSinDias).optional().map(this::conDias);
    }

    @Override
    public List<Racha> rachasActivas() {
        return jdbc.sql("SELECT * FROM cofre.racha WHERE fecha_fin IS NULL").query(this::mapRachaSinDias).list().stream().map(this::conDias).toList();
    }

    @Override
    public Racha crearRacha(long idUsuario, LocalDate inicio) {
        long id = jdbc.sql("INSERT INTO cofre.racha (id_usuario, fecha_inicio) VALUES (:u, :f) RETURNING id_racha")
                .param("u", idUsuario).param("f", inicio).query(Long.class).single();
        return new Racha(id, idUsuario, inicio, null, null, List.of());
    }

    @Override
    public void agregarDiaRacha(long idRacha, LocalDate fecha, String tipo) {
        jdbc.sql("INSERT INTO cofre.racha_dia (id_racha, fecha, tipo_dia) VALUES (:r, :f, :t) ON CONFLICT (id_racha, fecha) DO NOTHING")
                .param("r", idRacha).param("f", fecha).param("t", tipo).update();
    }

    @Override
    public void cerrarRacha(long idRacha, LocalDate fechaFin, String motivo) {
        jdbc.sql("UPDATE cofre.racha SET fecha_fin = :f, motivo_cierre = :m WHERE id_racha = :id")
                .param("f", fechaFin).param("m", motivo).param("id", idRacha).update();
    }

    @Override
    public List<Racha.Dia> diasRachaUsuario(long idUsuario, LocalDate desde, LocalDate hasta) {
        return jdbc.sql("""
                SELECT d.fecha, d.tipo_dia FROM cofre.racha_dia d JOIN cofre.racha r ON r.id_racha = d.id_racha
                 WHERE r.id_usuario = :u AND d.fecha BETWEEN :d AND :h ORDER BY d.fecha
                """).param("u", idUsuario).param("d", desde).param("h", hasta)
                .query((rs, i) -> new Racha.Dia(Jdbc.fecha(rs, "fecha"), rs.getString("tipo_dia"))).list();
    }

    // ---------------------------------------------------------------- sello

    @Override
    public Optional<SelloCompromiso> selloActivo(long idMeta) {
        return jdbc.sql("SELECT * FROM cofre.sello_compromiso WHERE id_meta = :m AND estatus = 'activo'").param("m", idMeta)
                .query(this::mapSello).optional();
    }

    @Override
    public Optional<SelloCompromiso> ultimoSello(long idMeta) {
        return jdbc.sql("SELECT * FROM cofre.sello_compromiso WHERE id_meta = :m ORDER BY id_sello DESC LIMIT 1").param("m", idMeta)
                .query(this::mapSello).optional();
    }

    @Override
    public SelloCompromiso guardarSello(SelloCompromiso s) {
        if (s.idSello() == null) {
            long id = jdbc.sql("""
                    INSERT INTO cofre.sello_compromiso (id_meta, monto_sellado_usd_cts, fecha_sellado, fecha_liberacion, estatus, fecha_ruptura)
                    VALUES (:m, :usd, :fs, :fl, :e, :fr) RETURNING id_sello
                    """).param("m", s.idMeta()).param("usd", s.montoSelladoUsdCts()).param("fs", s.fechaSellado())
                    .param("fl", s.fechaLiberacion()).param("e", s.estatus()).param("fr", s.fechaRuptura()).query(Long.class).single();
            return new SelloCompromiso(id, s.idMeta(), s.montoSelladoUsdCts(), s.fechaSellado(), s.fechaLiberacion(), s.estatus(), s.fechaRuptura());
        }
        jdbc.sql("UPDATE cofre.sello_compromiso SET estatus = :e, fecha_ruptura = :fr WHERE id_sello = :id")
                .param("e", s.estatus()).param("fr", s.fechaRuptura()).param("id", s.idSello()).update();
        return s;
    }

    // ---------------------------------------------------------------- Espejo de Impacto

    @Override
    public Optional<ReporteGuardado> reporteMensual(long idUsuario, int anio, int mes) {
        return jdbc.sql("SELECT * FROM cofre.reporte_mensual WHERE id_usuario = :u AND anio = :a AND mes = :m")
                .param("u", idUsuario).param("a", anio).param("m", mes)
                .query((rs, i) -> new ReporteGuardado(rs.getLong("id_usuario"), rs.getInt("anio"), rs.getInt("mes"), rs.getLong("total_bs_cts"),
                        rs.getLong("total_usd_cts"), rs.getLong("saldo_cofres_usd_cts"), rs.getLong("perdida_evitada_bs_cts"),
                        rs.getLong("tasa_inicio_bs_x10000"), rs.getLong("tasa_fin_bs_x10000"), Jdbc.enteroNulable(rs, "inflacion_mensual_pb"),
                        Jdbc.fechaHora(rs, "fecha_generacion", reloj.zona())))
                .optional();
    }

    @Override
    public void insertarReporteMensual(ReporteGuardado r) {
        jdbc.sql("""
                INSERT INTO cofre.reporte_mensual (id_usuario, anio, mes, total_bs_cts, total_usd_cts, saldo_cofres_usd_cts, perdida_evitada_bs_cts,
                    tasa_inicio_bs_x10000, tasa_fin_bs_x10000, inflacion_mensual_pb, fecha_generacion)
                VALUES (:u, :a, :m, :tbs, :tusd, :sc, :pe, :ti, :tf, :inf, :fg)
                ON CONFLICT (id_usuario, anio, mes) DO NOTHING
                """).param("u", r.idUsuario()).param("a", r.anio()).param("m", r.mes()).param("tbs", r.totalBsCts())
                .param("tusd", r.totalUsdCts()).param("sc", r.saldoCofresUsdCts()).param("pe", r.perdidaEvitadaBsCts())
                .param("ti", r.tasaInicioBsX10000()).param("tf", r.tasaFinBsX10000()).param("inf", r.inflacionMensualPb())
                .param("fg", r.fechaGeneracion()).update();
    }

    // ---------------------------------------------------------------- mapeos

    private MetaAhorro mapMeta(ResultSet rs, int i) throws SQLException {
        return new MetaAhorro(rs.getLong("id_meta"), rs.getLong("id_usuario"), rs.getString("nombre"), rs.getString("categoria"),
                rs.getLong("monto_objetivo_usd_cts"), Jdbc.fecha(rs, "fecha_objetivo"), rs.getString("frecuencia_cuota"),
                Jdbc.fechaHora(rs, "fecha_creacion", reloj.zona()), rs.getString("estatus"), Jdbc.fechaHora(rs, "fecha_cierre", reloj.zona()));
    }

    private Aporte mapAporte(ResultSet rs, int i) throws SQLException {
        return new Aporte(rs.getLong("id_aporte"), rs.getLong("id_meta"), Jdbc.largoNulable(rs, "id_cuenta"), Jdbc.largoNulable(rs, "id_regla"),
                Jdbc.largoNulable(rs, "id_cuota"), Jdbc.fechaHora(rs, "fecha_hora", reloj.zona()), rs.getString("tipo"), rs.getString("origen"),
                rs.getLong("monto_usd_cts"), rs.getLong("monto_bs_cts"), rs.getLong("tasa_valor_bs_x10000"), rs.getString("clave_origen"));
    }

    private CuotaGuardada mapCuota(ResultSet rs, int i) throws SQLException {
        return new CuotaGuardada(rs.getLong("id_cuota"), rs.getLong("id_meta"), Jdbc.fecha(rs, "fecha"), rs.getLong("monto_sugerido_usd_cts"),
                rs.getLong("monto_sugerido_bs_cts"), rs.getLong("tasa_valor_bs_x10000"), rs.getInt("periodos_restantes"), rs.getString("estatus"));
    }

    private ReglaAhorro mapRegla(ResultSet rs, int i) throws SQLException {
        return new ReglaAhorro(rs.getLong("id_regla"), rs.getLong("id_meta"), Jdbc.largoNulable(rs, "id_recomendacion"), rs.getString("tipo"),
                Jdbc.fechaHora(rs, "fecha_aprobacion", reloj.zona()), Jdbc.fechaHora(rs, "fecha_inicio_vigencia", reloj.zona()),
                Jdbc.fechaHora(rs, "fecha_fin_vigencia", reloj.zona()), rs.getString("estatus"), Jdbc.enteroNulable(rs, "porcentaje_pb"),
                Jdbc.largoNulable(rs, "multiplo_redondeo_cts"), Jdbc.largoNulable(rs, "monto_fijo_usd_cts"));
    }

    private Racha mapRachaSinDias(ResultSet rs, int i) throws SQLException {
        return new Racha(rs.getLong("id_racha"), rs.getLong("id_usuario"), Jdbc.fecha(rs, "fecha_inicio"), Jdbc.fecha(rs, "fecha_fin"),
                rs.getString("motivo_cierre"), List.of());
    }

    private Racha conDias(Racha r) {
        List<Racha.Dia> dias = jdbc.sql("SELECT fecha, tipo_dia FROM cofre.racha_dia WHERE id_racha = :r ORDER BY fecha").param("r", r.idRacha())
                .query((rs, i) -> new Racha.Dia(Jdbc.fecha(rs, "fecha"), rs.getString("tipo_dia"))).list();
        return new Racha(r.idRacha(), r.idUsuario(), r.fechaInicio(), r.fechaFin(), r.motivoCierre(), dias);
    }

    private SelloCompromiso mapSello(ResultSet rs, int i) throws SQLException {
        return new SelloCompromiso(rs.getLong("id_sello"), rs.getLong("id_meta"), rs.getLong("monto_sellado_usd_cts"),
                Jdbc.fechaHora(rs, "fecha_sellado", reloj.zona()), Jdbc.fecha(rs, "fecha_liberacion"), rs.getString("estatus"),
                Jdbc.fechaHora(rs, "fecha_ruptura", reloj.zona()));
    }
}
