package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.*;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.SelloCompromiso;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;

/** Metas de ahorro en USD (el cofre). */
@Service
class ServicioMetas implements GestionarMetas {

    private final RepositorioCofre repo;
    private final ServicioCuotas cuotas;
    private final UsuariosApi usuarios;
    private final Reloj reloj;
    private final Auditoria auditoria;

    ServicioMetas(RepositorioCofre repo, ServicioCuotas cuotas, UsuariosApi usuarios, Reloj reloj, Auditoria auditoria) {
        this.repo = repo;
        this.cuotas = cuotas;
        this.usuarios = usuarios;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional
    public VistaMeta crear(long idUsuario, NuevaMeta n) {
        usuarios.exigirKycAprobado(idUsuario);
        MetaAhorro meta = repo.guardarMeta(MetaAhorro.crear(idUsuario, n.nombre(), n.categoria(), n.montoObjetivoUsdCts(), n.fechaObjetivo(),
                n.frecuenciaCuota(), reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "META_CREADA", "META_AHORRO", meta.idMeta(),
                Map.of("objetivoUsdCts", meta.montoObjetivoUsdCts(), "fechaObjetivo", meta.fechaObjetivo().toString()));
        return vista(meta);
    }

    @Override
    @Transactional
    public List<VistaMeta> listar(long idUsuario) {
        return repo.metas(idUsuario).stream().map(this::vista).toList();
    }

    @Override
    @Transactional
    public VistaMeta obtener(long idUsuario, long idMeta) {
        return vista(propia(idUsuario, idMeta));
    }

    @Override
    @Transactional
    public VistaMeta cambiarEstatus(long idUsuario, long idMeta, String estatus) {
        MetaAhorro meta = repo.guardarMeta(propia(idUsuario, idMeta).cambiarEstatus(estatus, reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "META_" + estatus.toUpperCase(), "META_AHORRO", idMeta);
        return vista(meta);
    }

    MetaAhorro propia(long idUsuario, long idMeta) {
        return repo.meta(idMeta).filter(m -> m.idUsuario() == idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Meta"));
    }

    VistaMeta vista(MetaAhorro m) {
        long acumulado = repo.acumulado(m.idMeta());
        VistaSello sello = repo.ultimoSello(m.idMeta()).filter(s -> !"liberado".equals(s.estatus()) || m.activa())
                .map(ServicioMetas::vistaSello).orElse(null);
        VistaRuta cuota = m.activa() ? cuotas.vista(m, cuotas.cuotaHoy(m)) : null;
        return new VistaMeta(m.idMeta(), m.nombre(), m.categoria(), m.montoObjetivoUsdCts(), m.fechaObjetivo(), m.frecuenciaCuota(),
                m.estatus(), acumulado, m.progresoPb(acumulado), m.fechaCreacion(), sello, cuota);
    }

    static VistaSello vistaSello(SelloCompromiso s) {
        return new VistaSello(s.idSello(), s.montoSelladoUsdCts(), s.fechaLiberacion(), s.estatus(), s.fechaSellado(), s.fechaRuptura());
    }
}
