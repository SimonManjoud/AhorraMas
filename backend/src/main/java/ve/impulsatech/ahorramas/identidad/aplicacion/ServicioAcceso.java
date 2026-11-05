package ve.impulsatech.ahorramas.identidad.aplicacion;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.cifrado.ServicioCifrado;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.seguridad.NivelStepUp;
import ve.impulsatech.ahorramas.compartido.seguridad.ServicioTokens;
import ve.impulsatech.ahorramas.compartido.seguridad.ServicioTokens.TokenEmitido;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.DispositivoRegistrado;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.DispositivoRevocado;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.OtpEnviado;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.UsuarioRegistrado;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.entrada.CasosDeUsoIdentidad.*;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.RepositorioIdentidad;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.RepositorioIdentidad.CredencialPin;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.ServiciosExternosIdentidad.EnvioSms;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.ServiciosExternosIdentidad.HasherPin;
import ve.impulsatech.ahorramas.identidad.dominio.DesafioOtp;
import ve.impulsatech.ahorramas.identidad.dominio.Dispositivo;
import ve.impulsatech.ahorramas.identidad.dominio.Usuario;

/** Registro, login con PIN (huella en el dispositivo), OTP y step-up. */
@Service
class ServicioAcceso implements Acceso {

    static final long LIMITE_DIARIO_INICIAL_CTS = 50_000_000L;   // Bs 500.000,00
    static final int OPERACIONES_DIARIAS_INICIALES = 5;
    static final int MAX_FALLOS_PIN = 3;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final RepositorioIdentidad repo;
    private final HasherPin hasher;
    private final EnvioSms sms;
    private final ServicioTokens tokens;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final boolean exponerOtp;

    ServicioAcceso(RepositorioIdentidad repo, HasherPin hasher, EnvioSms sms, ServicioTokens tokens, Reloj reloj,
                   Auditoria auditoria, ApplicationEventPublisher eventos, @Value("${ahorra.demo.exponer-otp:false}") boolean exponerOtp) {
        this.repo = repo;
        this.hasher = hasher;
        this.sms = sms;
        this.tokens = tokens;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.exponerOtp = exponerOtp;
    }

    @Override
    @Transactional
    public Sesion registrar(ComandoRegistro c) {
        OffsetDateTime ahora = reloj.ahora();
        Usuario.validarPin(c.pin());
        if (c.cedula() == null || c.dispositivo() == null || c.dispositivo().huella() == null) {
            throw ErrorNegocio.validacion("Cédula y datos del dispositivo son obligatorios");
        }
        Usuario nuevo = Usuario.nuevo(c.nacionalidadDoc(), c.cedula(), c.primerNombre(), c.segundoNombre(), c.primerApellido(),
                c.segundoApellido(), c.fechaNacimiento(), c.correo(), c.telefonoMovil(), nuevoSeudonimo(), ahora);
        if (repo.usuarioPorDocumento(nuevo.nacionalidadDoc(), nuevo.cedula()).isPresent()) {
            throw ErrorNegocio.conflicto("Ya existe un usuario con esa cédula");
        }
        if (repo.existeCorreo(nuevo.correo())) {
            throw ErrorNegocio.conflicto("Ese correo ya está registrado");
        }
        if (repo.existeTelefono(nuevo.telefonoMovil())) {
            throw ErrorNegocio.conflicto("Ese teléfono ya está registrado");
        }
        Usuario usuario = repo.guardarUsuario(nuevo);
        long id = usuario.idUsuario();
        repo.guardarCredencial(id, hasher.hash(c.pin()), ahora);
        repo.crearLimite(id, LIMITE_DIARIO_INICIAL_CTS, OPERACIONES_DIARIAS_INICIALES, ahora, "Límite inicial");
        Dispositivo disp = repo.guardarDispositivo(Dispositivo.nuevo(id, huella(c.dispositivo().huella()),
                c.dispositivo().plataforma(), c.dispositivo().modelo(), ahora));

        auditoria.registrar(Actor.usuario, id, "USUARIO_REGISTRADO", "USUARIO", id, Map.of("dispositivo", disp.idDispositivo()));
        eventos.publishEvent(new UsuarioRegistrado(id, ahora));
        eventos.publishEvent(new DispositivoRegistrado(id, disp.idDispositivo(), disp.modelo(), true));
        return sesion(usuario, disp);
    }

    @Override
    @Transactional(noRollbackFor = ErrorNegocio.class)
    public Sesion iniciarSesion(ComandoLogin c) {
        OffsetDateTime ahora = reloj.ahora();
        if (c.cedula() == null || c.dispositivo() == null || c.dispositivo().huella() == null) {
            throw ErrorNegocio.validacion("Cédula, PIN y dispositivo son obligatorios");
        }
        Usuario usuario = repo.usuarioPorDocumento(c.nacionalidadDoc(), c.cedula())
                .orElseThrow(() -> ErrorNegocio.noAutenticado("Cédula o PIN incorrectos"));
        if (!usuario.activo()) {
            throw ErrorNegocio.prohibido("Tu usuario está " + usuario.estatus() + ". Contacta a soporte.");
        }
        long id = usuario.idUsuario();
        String huella = huella(c.dispositivo().huella());
        var existente = repo.dispositivoPorHuella(id, huella);
        Dispositivo disp = existente.orElseGet(() -> Dispositivo.nuevo(id, huella, c.dispositivo().plataforma(), c.dispositivo().modelo(), ahora));
        if (disp.reportadoRobado()) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.PROHIBIDO, "DISPOSITIVO_REVOCADO",
                    "Este dispositivo fue reportado como robado. Contacta a soporte.", Map.of());
        }

        verificarPin(usuario, disp.idDispositivo() == null ? null : disp, c.pin(), ahora);

        boolean nuevo = disp.idDispositivo() == null;
        boolean reactivado = !nuevo && !disp.activo();
        if (reactivado) {
            disp = disp.reactivar();
        }
        disp = (nuevo || reactivado) ? repo.guardarDispositivo(disp) : disp;
        if (nuevo) {
            eventos.publishEvent(new DispositivoRegistrado(id, disp.idDispositivo(), disp.modelo(), repo.contarDispositivos(id) == 1));
        }
        auditoria.registrar(Actor.usuario, id, "LOGIN", "DISPOSITIVO", disp.idDispositivo(),
                Map.of("nuevoDispositivo", nuevo, "reactivado", reactivado));
        return sesion(usuario, disp);
    }

    @Override
    @Transactional
    public DesafioEmitido solicitarOtp(long idUsuario, String proposito) {
        Usuario usuario = repo.usuarioPorId(idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario"));
        String prop = proposito == null || proposito.isBlank() ? "general" : proposito.trim();
        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        UUID id = UUID.randomUUID();
        OffsetDateTime expira = reloj.ahora().plusMinutes(5);
        repo.guardarDesafio(new DesafioOtp(id, idUsuario, prop.length() > 20 ? prop.substring(0, 20) : prop,
                hashOtp(id, codigo), expira, false, 0));
        sms.enviar(usuario.telefonoMovil(), "Ahorra+ OTP", "Tu código Ahorra+ es " + codigo + ". No lo compartas con nadie. Vence en 5 minutos.");
        eventos.publishEvent(new OtpEnviado(idUsuario, prop));
        auditoria.registrar(Actor.usuario, idUsuario, "OTP_SOLICITADO", "DESAFIO_OTP", id, Map.of("proposito", prop));
        return new DesafioEmitido(id, expira, exponerOtp ? codigo : null);
    }

    @Override
    @Transactional(noRollbackFor = ErrorNegocio.class)
    public StepUpEmitido stepUp(long idUsuario, long idDispositivo, String pin, UUID idDesafio, String otp) {
        OffsetDateTime ahora = reloj.ahora();
        Usuario usuario = repo.usuarioPorId(idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario"));
        Dispositivo disp = repo.dispositivo(idDispositivo).orElseThrow(() -> ErrorNegocio.noAutenticado("Dispositivo no reconocido"));
        verificarPin(usuario, disp, pin, ahora);
        NivelStepUp nivel = NivelStepUp.PIN;
        if (idDesafio != null) {
            DesafioOtp desafio = repo.desafio(idDesafio)
                    .filter(d -> d.idUsuario() == idUsuario)
                    .orElseThrow(() -> ErrorNegocio.regla("OTP_INVALIDO", "Código OTP inválido"));
            var resultado = desafio.verificar(hashOtp(idDesafio, otp == null ? "" : otp.trim()), ahora);
            repo.guardarDesafio(resultado.desafio());
            if (!resultado.valido()) {
                throw ErrorNegocio.regla("OTP_INVALIDO", "Código OTP incorrecto");
            }
            nivel = NivelStepUp.PIN_OTP;
        }
        TokenEmitido t = tokens.stepUp(idUsuario, idDispositivo, nivel);
        auditoria.registrar(Actor.usuario, idUsuario, "STEP_UP", "DISPOSITIVO", idDispositivo, Map.of("nivel", nivel.codigo()));
        return new StepUpEmitido(t.token(), nivel.codigo(), t.expiraEn());
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilUsuario perfil(long idUsuario) {
        Usuario u = repo.usuarioPorId(idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Usuario"));
        return new PerfilUsuario(u.idUsuario(), u.nacionalidadDoc(), u.cedula(), u.primerNombre(), u.primerApellido(), u.correo(),
                u.telefonoMovil(), repo.tieneKycAprobado(idUsuario), u.estatus(), u.fechaRegistro());
    }

    /** Verifica el PIN; a la 3.ª falla consecutiva revoca el dispositivo (F3 · revocación automática). */
    private void verificarPin(Usuario usuario, Dispositivo disp, String pin, OffsetDateTime ahora) {
        long id = usuario.idUsuario();
        CredencialPin cred = repo.credencial(id).orElseThrow(() -> ErrorNegocio.noAutenticado("Cédula o PIN incorrectos"));
        if (pin != null && hasher.coincide(pin, cred.pinHash())) {
            if (cred.intentosFallidos() > 0) {
                repo.actualizarIntentosFallidos(id, 0, ahora);
            }
            return;
        }
        int fallos = cred.intentosFallidos() + 1;
        auditoria.registrar(Actor.usuario, id, "PIN_FALLIDO", "USUARIO", id, Map.of("intento", fallos));
        if (fallos >= MAX_FALLOS_PIN) {
            repo.actualizarIntentosFallidos(id, 0, ahora);
            if (disp != null && disp.activo()) {
                repo.guardarDispositivo(disp.revocar(Dispositivo.Motivo.tres_fallas, ahora));
                eventos.publishEvent(new DispositivoRevocado(id, disp.idDispositivo(), "3_fallas"));
                auditoria.registrar(Actor.sistema, null, "DISPOSITIVO_REVOCADO", "DISPOSITIVO", disp.idDispositivo(), Map.of("motivo", "3_fallas"));
            }
            throw new ErrorNegocio(ErrorNegocio.Tipo.NO_AUTENTICADO, "DISPOSITIVO_REVOCADO",
                    "PIN incorrecto 3 veces: por seguridad cerramos la sesión de este dispositivo y anulamos el saldo rápido.",
                    Map.of("intentosRestantes", 0));
        }
        repo.actualizarIntentosFallidos(id, fallos, ahora);
        throw new ErrorNegocio(ErrorNegocio.Tipo.NO_AUTENTICADO, "PIN_INVALIDO", "Cédula o PIN incorrectos",
                Map.of("intentosRestantes", MAX_FALLOS_PIN - fallos));
    }

    private Sesion sesion(Usuario u, Dispositivo d) {
        TokenEmitido t = tokens.sesion(u.idUsuario(), d.idDispositivo());
        return new Sesion(t.token(), t.expiraEn(),
                new ResumenUsuario(u.idUsuario(), u.primerNombre(), u.primerApellido(), repo.tieneKycAprobado(u.idUsuario()), u.estatus()),
                d.idDispositivo());
    }

    private long nuevoSeudonimo() {
        long s;
        do {
            s = 100_000_000L + (long) (ALEATORIO.nextDouble() * 899_999_999L);
        } while (repo.existeSeudonimo(s));
        return s;
    }

    static String huella(String huellaCliente) {
        return ServicioCifrado.sha256Hex("disp:" + huellaCliente.trim());
    }

    private static String hashOtp(UUID id, String codigo) {
        return ServicioCifrado.sha256Hex(id + ":" + codigo);
    }
}
