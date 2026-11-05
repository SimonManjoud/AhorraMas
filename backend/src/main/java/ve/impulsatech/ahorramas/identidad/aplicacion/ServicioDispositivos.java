package ve.impulsatech.ahorramas.identidad.aplicacion;

import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.seguridad.ServicioTokens;
import ve.impulsatech.ahorramas.compartido.seguridad.ServicioTokens.TokenEmitido;
import ve.impulsatech.ahorramas.compartido.seguridad.ValidadorSesion;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.DispositivoRevocado;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.entrada.CasosDeUsoIdentidad.Dispositivos;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.RepositorioIdentidad;
import ve.impulsatech.ahorramas.identidad.dominio.Dispositivo;

/** Saldo rápido (F3) y revocación de dispositivos. También valida sesiones para la seguridad transversal. */
@Service
class ServicioDispositivos implements Dispositivos, ValidadorSesion {

    private final RepositorioIdentidad repo;
    private final ServicioTokens tokens;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;

    ServicioDispositivos(RepositorioIdentidad repo, ServicioTokens tokens, Reloj reloj, Auditoria auditoria, ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.tokens = tokens;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Override
    @Transactional
    public TokenEmitido activarSaldoRapido(long idUsuario, long idDispositivo) {
        Dispositivo d = propio(idUsuario, idDispositivo);
        repo.guardarDispositivo(d.conSaldoRapido(true, reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "SALDO_RAPIDO_ACTIVADO", "DISPOSITIVO", idDispositivo);
        return tokens.saldoRapido(idUsuario, idDispositivo);
    }

    @Override
    @Transactional
    public void desactivarSaldoRapido(long idUsuario, long idDispositivo) {
        Dispositivo d = propio(idUsuario, idDispositivo);
        repo.guardarDispositivo(d.conSaldoRapido(false, reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "SALDO_RAPIDO_DESACTIVADO", "DISPOSITIVO", idDispositivo);
    }

    @Override
    @Transactional
    public void revocar(long idUsuario, long idDispositivo, String motivo) {
        Dispositivo d = propio(idUsuario, idDispositivo);
        Dispositivo.Motivo m = Dispositivo.Motivo.de(motivo == null ? "usuario" : motivo);
        repo.guardarDispositivo(d.revocar(m, reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "DISPOSITIVO_REVOCADO", "DISPOSITIVO", idDispositivo, Map.of("motivo", m.codigo()));
        eventos.publishEvent(new DispositivoRevocado(idUsuario, idDispositivo, m.codigo()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean dispositivoActivo(long idUsuario, long idDispositivo) {
        return repo.dispositivo(idDispositivo).filter(d -> d.idUsuario() == idUsuario).map(Dispositivo::activo).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean saldoRapidoActivo(long idUsuario, long idDispositivo) {
        return repo.dispositivo(idDispositivo).filter(d -> d.idUsuario() == idUsuario)
                .map(d -> d.activo() && d.saldoRapidoActivo()).orElse(false);
    }

    private Dispositivo propio(long idUsuario, long idDispositivo) {
        Dispositivo d = repo.dispositivo(idDispositivo).orElseThrow(() -> ErrorNegocio.noEncontrado("Dispositivo"));
        if (d.idUsuario() != idUsuario) {
            throw ErrorNegocio.prohibido("El dispositivo no te pertenece");
        }
        return d;
    }
}
