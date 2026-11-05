package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.time.LocalDate;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.api.CofreApi;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;

/** Fachada pública del Cofre para otros módulos y el worker. */
@Service
class ServicioCofreApi implements CofreApi {

    private static final Logger log = LoggerFactory.getLogger(ServicioCofreApi.class);

    private final ServicioReglas reglas;
    private final ServicioRacha rachas;
    private final ServicioCuotas cuotas;
    private final ServicioEspejo espejo;
    private final RepositorioCofre repo;
    private final Reloj reloj;

    ServicioCofreApi(ServicioReglas reglas, ServicioRacha rachas, ServicioCuotas cuotas, ServicioEspejo espejo, RepositorioCofre repo,
                     Reloj reloj) {
        this.reglas = reglas;
        this.rachas = rachas;
        this.cuotas = cuotas;
        this.espejo = espejo;
        this.repo = repo;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public ReglaCreada crearReglaAprobada(long idUsuario, long idMeta, String tipo, long valor, Long idRecomendacion) {
        return switch (tipo) {
            case "pct_ingreso" -> reglas.crearAprobada(idUsuario, idMeta, tipo, (int) valor, null, null, idRecomendacion);
            case "redondeo" -> reglas.crearAprobada(idUsuario, idMeta, tipo, null, valor, null, idRecomendacion);
            default -> reglas.crearAprobada(idUsuario, idMeta, tipo, null, null, valor, idRecomendacion);
        };
    }

    @Override
    @Transactional(readOnly = true)
    public int metasActivas(long idUsuario) {
        return (int) repo.metas(idUsuario).stream().filter(MetaAhorro::activa).count();
    }

    /**
     * Cierre del día {@code fecha}: (1) racha/colchones, (2) cuotas vencidas, (3) reglas quincenales,
     * (4) metas vencidas y (5) Espejo de Impacto si fue fin de mes.
     */
    @Override
    public void cerrarDia(LocalDate fecha) {
        rachas.cerrarDiasHasta(fecha);
        cuotas.cerrarCuotasHasta(fecha);
        reglas.ejecutarQuincenales(fecha);
        vencerMetas(fecha);
        if (fecha.equals(YearMonth.from(fecha).atEndOfMonth())) {
            espejo.generarReportes(YearMonth.from(fecha));
        }
        log.info("Cierre del día {} completado", fecha);
    }

    @Transactional
    void vencerMetas(LocalDate fecha) {
        for (MetaAhorro m : repo.metasActivasDeTodos()) {
            if (m.fechaObjetivo().isBefore(fecha)) {
                repo.guardarMeta(m.cerrar("vencida", reloj.ahora()));
            }
        }
    }
}
