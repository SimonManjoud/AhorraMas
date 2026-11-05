package ve.impulsatech.ahorramas.consolidacion.aplicacion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.ConsentimientoActivado;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.ConsentimientoRevocado;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.entrada.CasosDeUsoConsolidacion.*;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.BovedaSecretos;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.OpenBanking;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.Repositorio;
import ve.impulsatech.ahorramas.consolidacion.dominio.Consentimiento;
import ve.impulsatech.ahorramas.consolidacion.dominio.Institucion;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;

/** F1 · Vinculación multibanco por consentimiento (sin claves bancarias). */
@Service
class ServicioConsentimientos implements GestionarConsentimientos {

    private final Repositorio repo;
    private final OpenBanking openBanking;
    private final BovedaSecretos boveda;
    private final ServicioSincronizacion sincronizacion;
    private final UsuariosApi usuarios;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;

    ServicioConsentimientos(Repositorio repo, OpenBanking openBanking, BovedaSecretos boveda, ServicioSincronizacion sincronizacion,
                            UsuariosApi usuarios, Reloj reloj, Auditoria auditoria, ApplicationEventPublisher eventos,
                            TransactionTemplate tx) {
        this.tx = tx;
        this.repo = repo;
        this.openBanking = openBanking;
        this.boveda = boveda;
        this.sincronizacion = sincronizacion;
        this.usuarios = usuarios;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Override
    public List<Institucion> instituciones() {
        return repo.instituciones();
    }

    /** Paso 1–2: selección múltiple → un consentimiento por banco con alcance mínimo. */
    @Override
    @Transactional
    public List<SolicitudCreada> solicitar(long idUsuario, List<Long> instituciones, boolean permiteDebitoTdc) {
        usuarios.exigirKycAprobado(idUsuario);
        if (instituciones == null || instituciones.isEmpty()) {
            throw ErrorNegocio.validacion("Selecciona al menos una institución");
        }
        OffsetDateTime ahora = reloj.ahora();
        String documento = usuarios.documento(idUsuario);
        List<SolicitudCreada> res = new ArrayList<>();
        for (Long idInst : instituciones.stream().distinct().toList()) {
            Institucion inst = repo.institucion(idInst).orElseThrow(() -> ErrorNegocio.noEncontrado("Institución " + idInst));
            var activo = repo.consentimientos(idUsuario).stream()
                    .filter(c -> c.idInstitucion() == idInst && c.activo(ahora)).findFirst();
            if (activo.isPresent()) {
                res.add(new SolicitudCreada(activo.get().idConsentimiento(), idInst, inst.nombre(), "activo", null));
                continue;
            }
            repo.vencerPendientes(idUsuario, idInst);
            Consentimiento c = repo.guardar(Consentimiento.solicitar(idUsuario, inst, permiteDebitoTdc, UUID.randomUUID().toString(), ahora));
            String url = openBanking.urlAutorizacion(inst.codigo(), c.estadoOauth(), c.alcances(), documento);
            auditoria.registrar(Actor.usuario, idUsuario, "CONSENTIMIENTO_SOLICITADO", "CONSENTIMIENTO", c.idConsentimiento(),
                    Map.of("institucion", inst.codigo(), "alcances", String.join(" ", c.alcances())));
            res.add(new SolicitudCreada(c.idConsentimiento(), idInst, inst.nombre(), c.estatus(), url));
        }
        return res;
    }

    /** Pasos 3–6: el banco redirige con el código → se canjea por token (mTLS) → bóveda → sincroniza. */
    @Override
    public ResultadoCallback completar(String estado, String codigo, String error) {
        if (estado == null || estado.isBlank()) {
            return new ResultadoCallback(false, null, "Solicitud inválida");
        }
        Consentimiento[] activado = new Consentimiento[1];
        ResultadoCallback r = tx.execute(s -> activar(estado, codigo, error, activado));
        if (activado[0] != null) {
            // Sincronización inicial en su propia transacción: si el banco falla, el consentimiento sigue activo.
            sincronizacion.sincronizar(activado[0]);
        }
        return r;
    }

    private ResultadoCallback activar(String estado, String codigo, String error, Consentimiento[] activado) {
        Consentimiento c = repo.consentimientoPorEstado(estado).filter(Consentimiento::pendiente).orElse(null);
        if (c == null) {
            return new ResultadoCallback(false, null, "Esta autorización ya fue procesada o expiró");
        }
        Institucion inst = repo.institucion(c.idInstitucion()).orElseThrow();
        OffsetDateTime ahora = reloj.ahora();
        if (error != null || codigo == null) {
            repo.guardar(c.revocar("rechazado_en_banco", ahora));
            auditoria.registrar(Actor.usuario, c.idUsuario(), "CONSENTIMIENTO_RECHAZADO", "CONSENTIMIENTO", c.idConsentimiento());
            return new ResultadoCallback(false, inst.nombre(), "No autorizaste el acceso en " + inst.nombre() + ". Puedes intentarlo de nuevo cuando quieras.");
        }
        String token = openBanking.canjearCodigo(inst.codigo(), codigo);
        String ref = boveda.guardar(token);
        Consentimiento activo = repo.guardar(c.activar(ref, ahora));
        auditoria.registrar(Actor.usuario, c.idUsuario(), "CONSENTIMIENTO_OTORGADO", "CONSENTIMIENTO", c.idConsentimiento(),
                Map.of("institucion", inst.codigo(), "debitoTdc", c.permiteDebitoTdc()));
        eventos.publishEvent(new ConsentimientoActivado(c.idUsuario(), c.idConsentimiento(), inst.idInstitucion(), inst.nombre(), c.permiteDebitoTdc()));
        activado[0] = activo;
        return new ResultadoCallback(true, inst.nombre(), "¡Listo! " + inst.nombre() + " quedó vinculado. Ya puedes volver a Ahorra+.");
    }

    @Override
    @Transactional(readOnly = true)
    public List<VistaConsentimiento> listar(long idUsuario) {
        var nombres = new java.util.HashMap<Long, String>();
        repo.instituciones().forEach(i -> nombres.put(i.idInstitucion(), i.nombre()));
        OffsetDateTime ahora = reloj.ahora();
        return repo.consentimientos(idUsuario).stream()
                .map(c -> new VistaConsentimiento(c.idConsentimiento(), c.idInstitucion(), nombres.get(c.idInstitucion()),
                        "activo".equals(c.estatus()) && !c.activo(ahora) ? "vencido" : c.estatus(),
                        c.permiteLectura(), c.permiteDebitoTdc(), c.fechaOtorgamiento(), c.fechaVencimiento()))
                .toList();
    }

    /** F1 · paso 7: el usuario revoca cuando quiera; el token se destruye en la bóveda. */
    @Override
    @Transactional
    public void revocar(long idUsuario, long idConsentimiento, String motivo) {
        Consentimiento c = repo.consentimiento(idConsentimiento).filter(x -> x.idUsuario() == idUsuario)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Consentimiento"));
        Institucion inst = repo.institucion(c.idInstitucion()).orElseThrow();
        if ("activo".equals(c.estatus()) && c.refTokenBoveda() != null) {
            try {
                openBanking.revocar(inst.codigo(), boveda.leer(c.refTokenBoveda()));
            } catch (RuntimeException ignorado) {
                // la revocación local procede aunque el banco no responda
            }
            boveda.destruir(c.refTokenBoveda());
        }
        String m = motivo == null || motivo.isBlank() ? "revocado_por_usuario" : motivo;
        repo.guardar(c.revocar(m, reloj.ahora()));
        repo.inactivarCuentas(idConsentimiento);
        auditoria.registrar(Actor.usuario, idUsuario, "CONSENTIMIENTO_REVOCADO", "CONSENTIMIENTO", idConsentimiento, Map.of("motivo", m));
        eventos.publishEvent(new ConsentimientoRevocado(idUsuario, idConsentimiento, inst.idInstitucion(), inst.nombre(), m));
    }
}
