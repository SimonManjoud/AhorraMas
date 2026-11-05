package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.api.CofreApi.ReglaCreada;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.GestionarReglas;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre.ReglaActivaDeUsuario;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.ReglaAhorro;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.MovimientoRegistrado;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;

/** Reglas de ahorro automático: % de ingreso, redondeo y aporte fijo quincenal (R-13). */
@Service
class ServicioReglas implements GestionarReglas, ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.EjecutarReglas {

    private static final Logger log = LoggerFactory.getLogger(ServicioReglas.class);

    private final RepositorioCofre repo;
    private final ServicioMetas metas;
    private final ServicioMovimientosCofre movimientos;
    private final TasasApi tasas;
    private final Reloj reloj;
    private final Auditoria auditoria;

    ServicioReglas(RepositorioCofre repo, ServicioMetas metas, ServicioMovimientosCofre movimientos, TasasApi tasas, Reloj reloj,
                   Auditoria auditoria) {
        this.repo = repo;
        this.metas = metas;
        this.movimientos = movimientos;
        this.tasas = tasas;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    /** La aprobación con huella/PIN la exige el adaptador web (step-up) antes de invocar. */
    @Override
    @Transactional
    public ReglaCreada crear(long idUsuario, long idMeta, String tipo, Integer porcentajePb, Long multiploRedondeoCts, Long montoFijoUsdCts) {
        return crearAprobada(idUsuario, idMeta, tipo, porcentajePb, multiploRedondeoCts, montoFijoUsdCts, null);
    }

    @Transactional
    ReglaCreada crearAprobada(long idUsuario, long idMeta, String tipo, Integer pct, Long multiplo, Long fijo, Long idRecomendacion) {
        MetaAhorro meta = metas.propia(idUsuario, idMeta);
        if (!meta.abierta()) {
            throw ErrorNegocio.regla("META_NO_ACTIVA", "La meta está " + meta.estatus());
        }
        boolean existe = repo.reglas(idMeta).stream().anyMatch(r -> r.tipo().equals(tipo) && !"finalizada".equals(r.estatus()));
        if (existe) {
            throw ErrorNegocio.conflicto("Esta meta ya tiene una regla de tipo " + tipo + ". Pausa o finaliza la actual primero.");
        }
        ReglaAhorro regla = repo.guardarRegla(ReglaAhorro.aprobar(idMeta, tipo, pct, multiplo, fijo, idRecomendacion, reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "REGLA_APROBADA", "REGLA_AHORRO", regla.idRegla(),
                Map.of("tipo", tipo, "meta", idMeta, "desdeIA", idRecomendacion != null));
        return vista(regla);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReglaCreada> listar(long idUsuario, long idMeta) {
        metas.propia(idUsuario, idMeta);
        return repo.reglas(idMeta).stream().map(ServicioReglas::vista).toList();
    }

    @Override
    @Transactional
    public ReglaCreada cambiarEstatus(long idUsuario, long idRegla, String estatus) {
        ReglaAhorro regla = repo.regla(idRegla).orElseThrow(() -> ErrorNegocio.noEncontrado("Regla"));
        metas.propia(idUsuario, regla.idMeta());
        ReglaAhorro nueva = repo.guardarRegla(regla.conEstatus(estatus, reloj.ahora()));
        auditoria.registrar(Actor.usuario, idUsuario, "REGLA_" + estatus.toUpperCase(), "REGLA_AHORRO", idRegla);
        return vista(nueva);
    }

    /** Worker: cada movimiento nuevo del banco se evalúa contra las reglas activas del usuario. */
    @Override
    public void ejecutarPorMovimiento(MovimientoRegistrado m) {
        List<ReglaActivaDeUsuario> reglas = repo.reglasActivasDeUsuario(m.idUsuario());
        if (reglas.isEmpty()) {
            return;
        }
        TasaBcv tasa = tasas.tasaDe(m.fechaHora().atZoneSameInstant(reloj.zona()).toLocalDate());
        for (ReglaActivaDeUsuario r : reglas) {
            // Las reglas no aplican a movimientos anteriores a su aprobación (los bancos informan la hora al segundo).
            if (m.fechaHora().isBefore(r.regla().fechaInicioVigencia().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) {
                continue;
            }
            long usd = r.regla().aporteParaMovimiento(m.clasificacion(), m.montoCts(), Moneda.de(m.moneda()), tasa);
            if (usd > 0) {
                movimientos.aportarPorRegla(r.regla().idRegla(), r.meta().idMeta(), usd,
                        "regla:" + r.regla().idRegla() + ":mov:" + m.idMovimiento());
            }
        }
    }

    /** Aporte fijo quincenal: días 15 y último de cada mes. */
    void ejecutarQuincenales(LocalDate fecha) {
        if (fecha.getDayOfMonth() != 15 && fecha.getDayOfMonth() != fecha.lengthOfMonth()) {
            return;
        }
        for (ReglaActivaDeUsuario r : repo.reglasActivasPorTipo("fijo_quincenal")) {
            try {
                movimientos.aportarPorRegla(r.regla().idRegla(), r.meta().idMeta(), r.regla().montoFijoUsdCts(),
                        "regla:" + r.regla().idRegla() + ":q:" + fecha);
            } catch (RuntimeException e) {
                log.warn("No se pudo ejecutar la regla quincenal {}: {}", r.regla().idRegla(), e.getMessage());
            }
        }
    }

    static ReglaCreada vista(ReglaAhorro r) {
        return new ReglaCreada(r.idRegla(), r.idMeta(), r.tipo(), r.porcentajePb(), r.multiploRedondeoCts(), r.montoFijoUsdCts(),
                r.estatus(), r.fechaAprobacion());
    }
}
