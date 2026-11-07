package ve.impulsatech.ahorramas.pagos.adaptador.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.*;
import ve.impulsatech.ahorramas.pagos.dominio.Conciliador;
import ve.impulsatech.ahorramas.pagos.dominio.EstadoPago;
import ve.impulsatech.ahorramas.pagos.dominio.PagoTdc;
import ve.impulsatech.ahorramas.pagos.dominio.TarifaComision;

/** Adaptador PostgreSQL del esquema {@code pagos}. */
@Repository
class PersistenciaPagosJdbc implements Repositorio {

    private static final String SQL_TARJETA = """
            SELECT t.*, e.id_estado_cuenta, e.fecha_corte, e.fecha_limite_pago, e.saldo_total_cts, e.pago_minimo_cts
              FROM pagos.tarjeta_credito t
              LEFT JOIN LATERAL (SELECT * FROM pagos.estado_cuenta_tdc ec WHERE ec.id_tarjeta = t.id_tarjeta
                                  ORDER BY ec.fecha_corte DESC LIMIT 1) e ON TRUE
            """;

    private final JdbcClient jdbc;
    private final Reloj reloj;

    PersistenciaPagosJdbc(JdbcClient jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public Optional<TarifaComision> tarifaVigente(LocalDate hoy) {
        return jdbc.sql("""
                SELECT id_tarifa, monto_fijo_cts, porcentaje_pb FROM pagos.tarifa_comision
                 WHERE fecha_inicio <= :h AND (fecha_fin IS NULL OR fecha_fin > :h) ORDER BY fecha_inicio DESC LIMIT 1
                """).param("h", hoy)
                .query((rs, i) -> new TarifaComision(rs.getLong(1), rs.getLong(2), Jdbc.enteroNulable(rs, "porcentaje_pb"))).optional();
    }

    @Override
    public long guardarTarjeta(long idUsuario, long idInstitucion, TarjetaExterna t, OffsetDateTime ahora) {
        return jdbc.sql("""
                INSERT INTO pagos.tarjeta_credito (id_usuario, id_institucion, ref_tarjeta_emisor, ultimos_4, titular_confirmado, es_propia,
                    fecha_registro, estatus, marca)
                VALUES (:u, :i, :ref, :u4, :tit, TRUE, :f, 'activa', :m)
                ON CONFLICT (id_usuario, id_institucion, ref_tarjeta_emisor) DO UPDATE SET ultimos_4 = EXCLUDED.ultimos_4, estatus = 'activa',
                    marca = COALESCE(EXCLUDED.marca, pagos.tarjeta_credito.marca)
                RETURNING id_tarjeta
                """).param("u", idUsuario).param("i", idInstitucion).param("ref", t.refTarjeta()).param("u4", t.ultimos4())
                .param("tit", t.titular() == null ? "TITULAR" : t.titular()).param("f", ahora).param("m", t.marca()).query(Long.class).single();
    }

    @Override
    public void guardarEstadoCuenta(long idTarjeta, EstadoCuentaExterno e) {
        jdbc.sql("""
                INSERT INTO pagos.estado_cuenta_tdc (id_tarjeta, fecha_corte, fecha_limite_pago, saldo_total_cts, pago_minimo_cts)
                VALUES (:t, :c, :l, :s, :m)
                ON CONFLICT (id_tarjeta, fecha_corte) DO UPDATE SET saldo_total_cts = EXCLUDED.saldo_total_cts,
                    pago_minimo_cts = EXCLUDED.pago_minimo_cts, fecha_limite_pago = EXCLUDED.fecha_limite_pago
                """).param("t", idTarjeta).param("c", e.fechaCorte()).param("l", e.fechaLimitePago()).param("s", e.saldoTotalCts())
                .param("m", e.pagoMinimoCts()).update();
    }

    @Override
    public List<TarjetaGuardada> tarjetas(long idUsuario) {
        return jdbc.sql(SQL_TARJETA + " WHERE t.id_usuario = :u ORDER BY t.id_tarjeta").param("u", idUsuario).query(this::mapTarjeta).list();
    }

    @Override
    public Optional<TarjetaGuardada> tarjeta(long idTarjeta) {
        return jdbc.sql(SQL_TARJETA + " WHERE t.id_tarjeta = :id").param("id", idTarjeta).query(this::mapTarjeta).optional();
    }

    @Override
    public void confirmarTitular(long idTarjeta, String titular) {
        jdbc.sql("UPDATE pagos.tarjeta_credito SET titular_confirmado = :t WHERE id_tarjeta = :id").param("t", titular).param("id", idTarjeta).update();
    }

    @Override
    public Optional<PagoTdc> insertarPago(PagoTdc p) {
        return jdbc.sql("""
                INSERT INTO pagos.pago_tdc (id_usuario, id_tarjeta, id_cuenta_origen, id_estado_cuenta, id_tarifa, clave_idempotencia, monto_cts,
                    comision_cts, fecha_hora_creacion, estado_actual, beneficiario_confirmado, fecha_hora_actualizacion)
                VALUES (:u, :t, :c, :ec, :tar, :clave, :m, :com, :f, :e, :b, :f)
                ON CONFLICT (clave_idempotencia) DO NOTHING
                RETURNING id_pago
                """).param("u", p.idUsuario()).param("t", p.idTarjeta()).param("c", p.idCuentaOrigen()).param("ec", p.idEstadoCuenta())
                .param("tar", p.idTarifa()).param("clave", p.claveIdempotencia()).param("m", p.montoCts()).param("com", p.comisionCts())
                .param("f", p.fechaHoraCreacion()).param("e", p.estado().name()).param("b", p.beneficiarioConfirmado())
                .query(Long.class).optional().map(p::conId);
    }

    @Override
    public Optional<PagoTdc> pago(long idPago) {
        return jdbc.sql("SELECT * FROM pagos.pago_tdc WHERE id_pago = :id").param("id", idPago).query(this::mapPago).optional();
    }

    @Override
    public Optional<PagoTdc> pagoParaActualizar(long idPago) {
        return jdbc.sql("SELECT * FROM pagos.pago_tdc WHERE id_pago = :id FOR UPDATE").param("id", idPago).query(this::mapPago).optional();
    }

    @Override
    public Optional<PagoTdc> pagoPorClave(String clave) {
        return jdbc.sql("SELECT * FROM pagos.pago_tdc WHERE clave_idempotencia = :c").param("c", clave).query(this::mapPago).optional();
    }

    @Override
    public List<PagoTdc> pagos(long idUsuario, int limite) {
        return jdbc.sql("SELECT * FROM pagos.pago_tdc WHERE id_usuario = :u ORDER BY fecha_hora_creacion DESC, id_pago DESC LIMIT :l")
                .param("u", idUsuario).param("l", limite).query(this::mapPago).list();
    }

    @Override
    public List<PagoTdc> pagosPorEstado(EstadoPago estado, int limite) {
        if (estado == null) {
            return jdbc.sql("SELECT * FROM pagos.pago_tdc ORDER BY id_pago DESC LIMIT :l").param("l", limite).query(this::mapPago).list();
        }
        return jdbc.sql("SELECT * FROM pagos.pago_tdc WHERE estado_actual = :e ORDER BY id_pago LIMIT :l")
                .param("e", estado.name()).param("l", limite).query(this::mapPago).list();
    }

    @Override
    public void actualizarPago(PagoTdc p, OffsetDateTime ahora) {
        jdbc.sql("""
                UPDATE pagos.pago_tdc SET estado_actual = :e, referencia_suiche = :ref, codigo_autorizacion = :aut, motivo_rechazo = :mot,
                    es_puntual = :pun, fecha_hora_captura = :cap, fecha_hora_actualizacion = :f
                 WHERE id_pago = :id
                """).param("e", p.estado().name()).param("ref", p.referenciaSuiche()).param("aut", p.codigoAutorizacion())
                .param("mot", p.motivoRechazo()).param("pun", p.esPuntual()).param("cap", p.fechaHoraCaptura()).param("f", ahora)
                .param("id", p.idPago()).update();
    }

    @Override
    public void insertarHistorial(long idPago, EstadoPago anterior, EstadoPago nuevo, String actor, String ref, OffsetDateTime fecha) {
        jdbc.sql("""
                INSERT INTO pagos.historial_estado_pago (id_pago, secuencia, estado_anterior, estado_nuevo, fecha_hora, actor, referencia_suiche)
                VALUES (:p, (SELECT COALESCE(MAX(secuencia), 0) + 1 FROM pagos.historial_estado_pago WHERE id_pago = :p), :a, :n, :f, :act, :ref)
                """).param("p", idPago).param("a", anterior == null ? null : anterior.name()).param("n", nuevo.name()).param("f", fecha)
                .param("act", actor).param("ref", ref).update();
    }

    @Override
    public List<Historial> historial(long idPago) {
        return jdbc.sql("SELECT * FROM pagos.historial_estado_pago WHERE id_pago = :p ORDER BY secuencia").param("p", idPago)
                .query((rs, i) -> new Historial(rs.getInt("secuencia"), rs.getString("estado_anterior"), rs.getString("estado_nuevo"),
                        Jdbc.fechaHora(rs, "fecha_hora", reloj.zona()), rs.getString("actor"), rs.getString("referencia_suiche")))
                .list();
    }

    @Override
    public void registrarMensaje(long idPago, String tipo, OffsetDateTime envio, OffsetDateTime respuesta, String resultado, String codigo) {
        jdbc.sql("""
                INSERT INTO pagos.mensaje_suiche (id_pago, tipo, numero_intento, fecha_hora_envio, fecha_hora_respuesta, resultado, codigo_respuesta)
                VALUES (:p, :t, (SELECT COUNT(*) + 1 FROM pagos.mensaje_suiche WHERE id_pago = :p AND tipo = :t), :e, :r, :res, :c)
                """).param("p", idPago).param("t", tipo).param("e", envio).param("r", respuesta).param("res", resultado)
                .param("c", codigo == null ? null : codigo.length() > 10 ? codigo.substring(0, 10) : codigo).update();
    }

    @Override
    public UsoDiario usoDelDia(long idUsuario, OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(monto_cts), 0), COUNT(*) FROM pagos.pago_tdc
                 WHERE id_usuario = :u AND fecha_hora_creacion >= :d AND fecha_hora_creacion < :h AND estado_actual <> 'RECHAZADO'
                """).param("u", idUsuario).param("d", desde).param("h", hasta)
                .query((rs, i) -> new UsoDiario(rs.getLong(1), rs.getInt(2))).single();
    }

    @Override
    public int intentosDesde(long idUsuario, OffsetDateTime desde) {
        return jdbc.sql("SELECT COUNT(*) FROM pagos.pago_tdc WHERE id_usuario = :u AND fecha_hora_creacion >= :d")
                .param("u", idUsuario).param("d", desde).query(Integer.class).single();
    }

    @Override
    public void bloquearUsuario(long idUsuario) {
        jdbc.sql("SELECT pg_advisory_xact_lock(31, CAST(:u AS integer))").param("u", (int) idUsuario).query().singleRow();
    }

    @Override
    public Optional<ArchivoGuardado> archivo(LocalDate fecha) {
        return jdbc.sql("SELECT * FROM pagos.archivo_liquidacion WHERE fecha_liquidacion = :f").param("f", fecha)
                .query((rs, i) -> new ArchivoGuardado(rs.getLong("id_archivo"), Jdbc.fecha(rs, "fecha_liquidacion"), rs.getString("nombre_archivo"),
                        rs.getString("hash_archivo"), rs.getInt("total_registros"), rs.getLong("monto_total_cts"), rs.getString("estatus")))
                .optional();
    }

    @Override
    public long insertarArchivo(ArchivoLiquidacion a, OffsetDateTime recepcion, String estatus) {
        long id = jdbc.sql("""
                INSERT INTO pagos.archivo_liquidacion (fecha_liquidacion, fecha_hora_recepcion, nombre_archivo, hash_archivo, total_registros,
                    monto_total_cts, estatus)
                VALUES (:f, :r, :n, :h, :t, :m, :e) RETURNING id_archivo
                """).param("f", a.fechaLiquidacion()).param("r", recepcion).param("n", a.nombreArchivo()).param("h", a.hashArchivo())
                .param("t", a.totalRegistros()).param("m", a.montoTotalCts()).param("e", estatus).query(Long.class).single();
        for (RegistroArchivo r : a.registros()) {
            jdbc.sql("""
                    INSERT INTO pagos.registro_liquidacion (id_archivo, linea, referencia_suiche, referencia, monto_cts, estado_suiche, fecha_hora_operacion)
                    VALUES (:a, :l, :rs, :r, :m, :e, :f)
                    """).param("a", id).param("l", r.linea()).param("rs", r.referenciaSuiche()).param("r", r.referencia())
                    .param("m", r.montoCts()).param("e", r.estado()).param("f", r.fechaHoraOperacion()).update();
        }
        return id;
    }

    @Override
    public void actualizarEstatusArchivo(long idArchivo, String estatus) {
        jdbc.sql("UPDATE pagos.archivo_liquidacion SET estatus = :e WHERE id_archivo = :id").param("e", estatus).param("id", idArchivo).update();
    }

    @Override
    public List<PagoTdc> pagosCapturadosEntre(OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                SELECT * FROM pagos.pago_tdc WHERE fecha_hora_captura >= :d AND fecha_hora_captura < :h
                   AND estado_actual IN ('CAPTURADO', 'REVERSADO') ORDER BY id_pago
                """).param("d", desde).param("h", hasta).query(this::mapPago).list();
    }

    @Override
    public List<PagoTdc> pagosPorReferencias(List<String> refsSuiche, List<String> claves) {
        if (refsSuiche.isEmpty() && claves.isEmpty()) {
            return List.of();
        }
        List<String> rs = refsSuiche.isEmpty() ? List.of("-") : refsSuiche;
        List<String> cl = claves.stream().filter(java.util.Objects::nonNull).toList();
        return jdbc.sql("SELECT * FROM pagos.pago_tdc WHERE referencia_suiche IN (:rs) OR clave_idempotencia IN (:cl) ORDER BY id_pago")
                .param("rs", rs).param("cl", cl.isEmpty() ? List.of("-") : cl).query(this::mapPago).list();
    }

    @Override
    public void insertarResultado(long idArchivo, LocalDate fecha, Conciliador.Resultado r) {
        jdbc.sql("""
                INSERT INTO pagos.resultado_conciliacion (id_pago, id_archivo, linea, fecha_conciliacion, resultado, diferencia_cts)
                VALUES (:p, :a, :l, :f, :r, :d)
                """).param("p", r.idPago()).param("a", idArchivo).param("l", r.linea()).param("f", fecha).param("r", r.tipo().name())
                .param("d", r.diferenciaCts()).update();
    }

    @Override
    public List<Conciliador.Resultado> resultados(long idArchivo) {
        return jdbc.sql("SELECT * FROM pagos.resultado_conciliacion WHERE id_archivo = :a ORDER BY id_resultado").param("a", idArchivo)
                .query((rs, i) -> new Conciliador.Resultado(Jdbc.largoNulable(rs, "id_pago"), Jdbc.enteroNulable(rs, "linea"),
                        Conciliador.Tipo.valueOf(rs.getString("resultado")), Jdbc.largoNulable(rs, "diferencia_cts")))
                .list();
    }

    private TarjetaGuardada mapTarjeta(ResultSet rs, int i) throws SQLException {
        Long idEc = Jdbc.largoNulable(rs, "id_estado_cuenta");
        EstadoCuentaExterno ec = idEc == null ? null : new EstadoCuentaExterno(Jdbc.fecha(rs, "fecha_corte"), Jdbc.fecha(rs, "fecha_limite_pago"),
                rs.getLong("saldo_total_cts"), rs.getLong("pago_minimo_cts"));
        return new TarjetaGuardada(rs.getLong("id_tarjeta"), rs.getLong("id_usuario"), rs.getLong("id_institucion"), rs.getString("ref_tarjeta_emisor"),
                rs.getString("ultimos_4"), rs.getString("titular_confirmado"), rs.getBoolean("es_propia"), rs.getString("alias_usuario"),
                rs.getString("estatus"), idEc, ec, rs.getString("marca"));
    }

    private PagoTdc mapPago(ResultSet rs, int i) throws SQLException {
        var z = reloj.zona();
        return new PagoTdc(rs.getLong("id_pago"), rs.getLong("id_usuario"), rs.getLong("id_tarjeta"), rs.getLong("id_cuenta_origen"),
                Jdbc.largoNulable(rs, "id_estado_cuenta"), rs.getLong("id_tarifa"), rs.getString("clave_idempotencia"), rs.getLong("monto_cts"),
                rs.getLong("comision_cts"), Jdbc.fechaHora(rs, "fecha_hora_creacion", z), EstadoPago.valueOf(rs.getString("estado_actual")),
                rs.getBoolean("beneficiario_confirmado"), rs.getString("referencia_suiche"), rs.getString("codigo_autorizacion"),
                rs.getString("motivo_rechazo"), Jdbc.booleanoNulable(rs, "es_puntual"), Jdbc.fechaHora(rs, "fecha_hora_captura", z));
    }
}
