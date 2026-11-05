package ve.impulsatech.ahorramas.identidad.adaptador.salida.persistencia;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.compartido.cifrado.ServicioCifrado;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi.LimiteVigente;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.RepositorioIdentidad;
import ve.impulsatech.ahorramas.identidad.dominio.DesafioOtp;
import ve.impulsatech.ahorramas.identidad.dominio.Dispositivo;
import ve.impulsatech.ahorramas.identidad.dominio.Usuario;
import ve.impulsatech.ahorramas.identidad.dominio.VerificacionKyc;

/** Adaptador PostgreSQL del esquema {@code identidad}. Cifra la PII (correo, teléfono) en reposo. */
@Repository
class PersistenciaIdentidadJdbc implements RepositorioIdentidad {

    private final JdbcClient jdbc;
    private final ServicioCifrado cifrado;
    private final Reloj reloj;

    PersistenciaIdentidadJdbc(JdbcClient jdbc, ServicioCifrado cifrado, Reloj reloj) {
        this.jdbc = jdbc;
        this.cifrado = cifrado;
        this.reloj = reloj;
    }

    @Override
    public Usuario guardarUsuario(Usuario u) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO identidad.usuario (nacionalidad_doc, cedula, primer_nombre, segundo_nombre, primer_apellido, segundo_apellido,
                    fecha_nacimiento, correo_cifrado, correo_indice, telefono_cifrado, telefono_indice, id_seudonimo, fecha_registro, estatus)
                VALUES (:nac, :cedula, :pn, :sn, :pa, :sa, :fnac, :correo, :correoIdx, :tel, :telIdx, :seud, :freg, :estatus)
                """)
                .param("nac", u.nacionalidadDoc()).param("cedula", u.cedula())
                .param("pn", u.primerNombre()).param("sn", u.segundoNombre())
                .param("pa", u.primerApellido()).param("sa", u.segundoApellido())
                .param("fnac", u.fechaNacimiento())
                .param("correo", cifrado.cifrarPii(u.correo())).param("correoIdx", cifrado.indiceCiego("correo:" + u.correo()))
                .param("tel", cifrado.cifrarPii(u.telefonoMovil())).param("telIdx", cifrado.indiceCiego("tel:" + u.telefonoMovil()))
                .param("seud", u.idSeudonimo()).param("freg", u.fechaRegistro()).param("estatus", u.estatus())
                .update(kh, "id_usuario");
        return u.conId(kh.getKey().longValue());
    }

    @Override
    public Optional<Usuario> usuarioPorDocumento(String nac, int cedula) {
        return jdbc.sql("SELECT * FROM identidad.usuario WHERE nacionalidad_doc = :nac AND cedula = :cedula")
                .param("nac", nac).param("cedula", cedula).query(this::usuario).optional();
    }

    @Override
    public Optional<Usuario> usuarioPorId(long id) {
        return jdbc.sql("SELECT * FROM identidad.usuario WHERE id_usuario = :id").param("id", id).query(this::usuario).optional();
    }

    @Override
    public boolean existeCorreo(String correo) {
        return existe("SELECT COUNT(*) FROM identidad.usuario WHERE correo_indice = :v", cifrado.indiceCiego("correo:" + correo));
    }

    @Override
    public boolean existeTelefono(String tel) {
        return existe("SELECT COUNT(*) FROM identidad.usuario WHERE telefono_indice = :v", cifrado.indiceCiego("tel:" + tel));
    }

    @Override
    public boolean existeSeudonimo(long s) {
        return existe("SELECT COUNT(*) FROM identidad.usuario WHERE id_seudonimo = :v", s);
    }

    @Override
    public void guardarCredencial(long idUsuario, String pinHash, OffsetDateTime ahora) {
        jdbc.sql("""
                INSERT INTO identidad.credencial (id_usuario, pin_hash, intentos_fallidos, fecha_actualizacion) VALUES (:id, :h, 0, :f)
                ON CONFLICT (id_usuario) DO UPDATE SET pin_hash = EXCLUDED.pin_hash, intentos_fallidos = 0, fecha_actualizacion = EXCLUDED.fecha_actualizacion
                """).param("id", idUsuario).param("h", pinHash).param("f", ahora).update();
    }

    @Override
    public Optional<CredencialPin> credencial(long idUsuario) {
        return jdbc.sql("SELECT id_usuario, pin_hash, intentos_fallidos FROM identidad.credencial WHERE id_usuario = :id")
                .param("id", idUsuario)
                .query((rs, i) -> new CredencialPin(rs.getLong(1), rs.getString(2), rs.getInt(3))).optional();
    }

    @Override
    public void actualizarIntentosFallidos(long idUsuario, int intentos, OffsetDateTime ahora) {
        jdbc.sql("UPDATE identidad.credencial SET intentos_fallidos = :n, fecha_actualizacion = :f WHERE id_usuario = :id")
                .param("n", intentos).param("f", ahora).param("id", idUsuario).update();
    }

    @Override
    public Dispositivo guardarDispositivo(Dispositivo d) {
        if (d.idDispositivo() == null) {
            GeneratedKeyHolder kh = new GeneratedKeyHolder();
            jdbc.sql("""
                    INSERT INTO identidad.dispositivo (id_usuario, huella_dispositivo, plataforma, modelo, fecha_registro, biometria_vinculada,
                        saldo_rapido_activo, fecha_activ_saldo_rapido, estatus, fecha_revocacion, motivo_revocacion)
                    VALUES (:u, :h, :p, :m, :f, :b, :sr, :fsr, :e, :fr, :mr)
                    """)
                    .param("u", d.idUsuario()).param("h", d.huella()).param("p", d.plataforma()).param("m", d.modelo())
                    .param("f", d.fechaRegistro()).param("b", d.biometriaVinculada()).param("sr", d.saldoRapidoActivo())
                    .param("fsr", d.fechaActivSaldoRapido()).param("e", d.estatus()).param("fr", d.fechaRevocacion())
                    .param("mr", d.motivoRevocacion())
                    .update(kh, "id_dispositivo");
            return dispositivo(kh.getKey().longValue()).orElseThrow();
        }
        jdbc.sql("""
                UPDATE identidad.dispositivo SET saldo_rapido_activo = :sr, fecha_activ_saldo_rapido = :fsr, estatus = :e,
                    fecha_revocacion = :fr, motivo_revocacion = :mr, biometria_vinculada = :b WHERE id_dispositivo = :id
                """)
                .param("sr", d.saldoRapidoActivo()).param("fsr", d.fechaActivSaldoRapido()).param("e", d.estatus())
                .param("fr", d.fechaRevocacion()).param("mr", d.motivoRevocacion()).param("b", d.biometriaVinculada())
                .param("id", d.idDispositivo()).update();
        return d;
    }

    @Override
    public Optional<Dispositivo> dispositivoPorHuella(long idUsuario, String huella) {
        return jdbc.sql("SELECT * FROM identidad.dispositivo WHERE id_usuario = :u AND huella_dispositivo = :h")
                .param("u", idUsuario).param("h", huella).query(this::dispositivoFila).optional();
    }

    @Override
    public Optional<Dispositivo> dispositivo(long id) {
        return jdbc.sql("SELECT * FROM identidad.dispositivo WHERE id_dispositivo = :id").param("id", id).query(this::dispositivoFila).optional();
    }

    @Override
    public int contarDispositivos(long idUsuario) {
        return jdbc.sql("SELECT COUNT(*) FROM identidad.dispositivo WHERE id_usuario = :u").param("u", idUsuario).query(Integer.class).single();
    }

    @Override
    public VerificacionKyc guardarVerificacion(VerificacionKyc v) {
        GeneratedKeyHolder kh = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO identidad.verificacion_kyc (id_usuario, fecha_solicitud, fecha_resultado, resultado, prueba_vida_superada,
                    coincide_lista_sanciones, ref_proveedor_kyc, motivo_rechazo)
                VALUES (:u, :fs, :fr, :r, :pv, :ls, :ref, :mot)
                """)
                .param("u", v.idUsuario()).param("fs", v.fechaSolicitud()).param("fr", v.fechaResultado()).param("r", v.resultado())
                .param("pv", v.pruebaVidaSuperada()).param("ls", v.coincideListaSanciones()).param("ref", v.refProveedorKyc())
                .param("mot", v.motivoRechazo())
                .update(kh, "id_verificacion");
        long id = kh.getKey().longValue();
        for (VerificacionKyc.Documento d : v.documentos()) {
            jdbc.sql("""
                    INSERT INTO identidad.documento_kyc (id_verificacion, secuencia, tipo, ref_almacen_objetos, hash_sha256, fecha_carga)
                    VALUES (:v, :s, :t, :ref, :h, :f)
                    """).param("v", id).param("s", d.secuencia()).param("t", d.tipo()).param("ref", d.refAlmacenObjetos())
                    .param("h", d.hashSha256()).param("f", d.fechaCarga()).update();
        }
        return new VerificacionKyc(id, v.idUsuario(), v.fechaSolicitud(), v.fechaResultado(), v.resultado(), v.pruebaVidaSuperada(),
                v.coincideListaSanciones(), v.refProveedorKyc(), v.motivoRechazo(), v.documentos());
    }

    @Override
    public boolean tieneKycAprobado(long idUsuario) {
        return existe("SELECT COUNT(*) FROM identidad.verificacion_kyc WHERE id_usuario = :v AND resultado = 'aprobado'", idUsuario);
    }

    @Override
    public void guardarDesafio(DesafioOtp d) {
        jdbc.sql("""
                INSERT INTO identidad.desafio_otp (id_desafio, id_usuario, proposito, codigo_hash, expira_en, usado, intentos)
                VALUES (:id, :u, :p, :h, :e, :usado, :n)
                ON CONFLICT (id_desafio) DO UPDATE SET usado = EXCLUDED.usado, intentos = EXCLUDED.intentos
                """).param("id", d.idDesafio()).param("u", d.idUsuario()).param("p", d.proposito()).param("h", d.codigoHash())
                .param("e", d.expiraEn()).param("usado", d.usado()).param("n", d.intentos()).update();
    }

    @Override
    public Optional<DesafioOtp> desafio(UUID id) {
        return jdbc.sql("SELECT * FROM identidad.desafio_otp WHERE id_desafio = :id").param("id", id)
                .query((rs, i) -> new DesafioOtp(rs.getObject("id_desafio", UUID.class), rs.getLong("id_usuario"), rs.getString("proposito"),
                        rs.getString("codigo_hash"), Jdbc.fechaHora(rs, "expira_en", reloj.zona()), rs.getBoolean("usado"), rs.getInt("intentos")))
                .optional();
    }

    @Override
    public Optional<LimiteVigente> limiteVigente(long idUsuario) {
        return jdbc.sql("""
                SELECT monto_max_diario_cts, max_operaciones_diarias FROM identidad.limite_transaccional
                 WHERE id_usuario = :u AND fecha_fin IS NULL
                """).param("u", idUsuario)
                .query((rs, i) -> new LimiteVigente(rs.getLong(1), rs.getInt(2))).optional();
    }

    @Override
    public void cerrarLimiteVigente(long idUsuario, OffsetDateTime fechaFin) {
        jdbc.sql("UPDATE identidad.limite_transaccional SET fecha_fin = :f WHERE id_usuario = :u AND fecha_fin IS NULL")
                .param("f", fechaFin).param("u", idUsuario).update();
    }

    @Override
    public void crearLimite(long idUsuario, long monto, int ops, OffsetDateTime inicio, String motivo) {
        jdbc.sql("""
                INSERT INTO identidad.limite_transaccional (id_usuario, monto_max_diario_cts, max_operaciones_diarias, fecha_inicio, motivo_cambio)
                VALUES (:u, :m, :o, :f, :mot)
                """).param("u", idUsuario).param("m", monto).param("o", ops).param("f", inicio).param("mot", motivo).update();
    }

    private boolean existe(String sql, Object valor) {
        return jdbc.sql(sql).param("v", valor).query(Long.class).single() > 0;
    }

    private Usuario usuario(ResultSet rs, int i) throws SQLException {
        return new Usuario(rs.getLong("id_usuario"), rs.getString("nacionalidad_doc"), rs.getInt("cedula"), rs.getString("primer_nombre"),
                rs.getString("segundo_nombre"), rs.getString("primer_apellido"), rs.getString("segundo_apellido"),
                Jdbc.fecha(rs, "fecha_nacimiento"), cifrado.descifrarPii(rs.getString("correo_cifrado")),
                cifrado.descifrarPii(rs.getString("telefono_cifrado")), rs.getLong("id_seudonimo"),
                Jdbc.fechaHora(rs, "fecha_registro", reloj.zona()), rs.getString("estatus"));
    }

    private Dispositivo dispositivoFila(ResultSet rs, int i) throws SQLException {
        return new Dispositivo(rs.getLong("id_dispositivo"), rs.getLong("id_usuario"), rs.getString("huella_dispositivo"),
                rs.getString("plataforma"), rs.getString("modelo"), Jdbc.fechaHora(rs, "fecha_registro", reloj.zona()),
                rs.getBoolean("biometria_vinculada"), rs.getBoolean("saldo_rapido_activo"),
                Jdbc.fechaHora(rs, "fecha_activ_saldo_rapido", reloj.zona()), rs.getString("estatus"),
                Jdbc.fechaHora(rs, "fecha_revocacion", reloj.zona()), rs.getString("motivo_revocacion"));
    }
}
