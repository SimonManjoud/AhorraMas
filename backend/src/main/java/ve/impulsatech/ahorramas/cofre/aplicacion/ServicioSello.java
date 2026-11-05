package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.GestionarSello;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.VistaSello;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.SelloCompromiso;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;

/** Engranaje 4 · Sello de compromiso suave (R-12). */
@Service
class ServicioSello implements GestionarSello {

    private final RepositorioCofre repo;
    private final ServicioMovimientosCofre movimientos;
    private final Reloj reloj;
    private final Auditoria auditoria;

    ServicioSello(RepositorioCofre repo, ServicioMovimientosCofre movimientos, Reloj reloj, Auditoria auditoria) {
        this.repo = repo;
        this.movimientos = movimientos;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional
    public VistaSello sellar(long idUsuario, long idMeta, long montoUsdCts) {
        MetaAhorro meta = repo.metaParaActualizar(idMeta).filter(m -> m.idUsuario() == idUsuario)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Meta"));
        SelloCompromiso sello = repo.guardarSello(SelloCompromiso.sellar(meta, montoUsdCts, repo.acumulado(idMeta),
                repo.selloActivo(idMeta).isPresent(), reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "SELLO_CREADO", "SELLO_COMPROMISO", sello.idSello(),
                Map.of("meta", idMeta, "usdCts", montoUsdCts, "liberacion", sello.fechaLiberacion().toString()));
        return ServicioMetas.vistaSello(sello);
    }

    @Override
    @Transactional
    public VistaSello romper(long idUsuario, long idMeta) {
        MetaAhorro meta = repo.metaParaActualizar(idMeta).filter(m -> m.idUsuario() == idUsuario)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Meta"));
        SelloCompromiso sello = repo.selloActivo(idMeta).orElseThrow(() -> ErrorNegocio.regla("R-12", "La meta no tiene un sello activo"));
        movimientos.romperSello(meta, sello, reloj.ahora());
        return repo.ultimoSello(idMeta).map(ServicioMetas::vistaSello).orElseThrow();
    }
}
