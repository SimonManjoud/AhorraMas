package ve.impulsatech.ahorramas.notificaciones.aplicacion;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.puerto.PuertosNotificaciones.CanalNotificaciones;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.puerto.PuertosNotificaciones.Notificacion;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.puerto.PuertosNotificaciones.Repositorio;

/** Registra y envía notificaciones; si el canal falla queda como "fallida" (no se pierde). */
@Service
public class ServicioNotificaciones {

    private final Repositorio repo;
    private final CanalNotificaciones canal;
    private final UsuariosApi usuarios;
    private final Reloj reloj;

    ServicioNotificaciones(Repositorio repo, CanalNotificaciones canal, UsuariosApi usuarios, Reloj reloj) {
        this.repo = repo;
        this.canal = canal;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    @Transactional
    public void push(long idUsuario, String tipo, String titulo, String mensaje) {
        enviar(idUsuario, "push", tipo, titulo, mensaje);
    }

    /** Registro de un SMS ya enviado por otro módulo (p. ej. OTP; el código nunca se guarda). */
    @Transactional
    public void registrarSms(long idUsuario, String tipo, String titulo, String mensaje) {
        repo.insertar(idUsuario, "sms", tipo, titulo, mensaje, "enviada", reloj.ahora());
    }

    private void enviar(long idUsuario, String canalNombre, String tipo, String titulo, String mensaje) {
        boolean ok;
        try {
            String destino = "push".equals(canalNombre) ? "usuario:" + idUsuario : usuarios.contacto(idUsuario).telefono();
            ok = canal.enviar(canalNombre, destino, titulo, mensaje, tipo);
        } catch (RuntimeException e) {
            ok = false;
        }
        repo.insertar(idUsuario, canalNombre, tipo, titulo, mensaje, ok ? "enviada" : "fallida", reloj.ahora());
    }

    @Transactional(readOnly = true)
    public List<Notificacion> listar(long idUsuario) {
        return repo.listar(idUsuario, 100);
    }

    @Transactional
    public void marcarLeida(long idUsuario, long idNotificacion) {
        if (!repo.marcarLeida(idUsuario, idNotificacion, reloj.ahora())) {
            throw ErrorNegocio.noEncontrado("Notificación");
        }
    }

    public OffsetDateTime ahora() {
        return reloj.ahora();
    }
}
