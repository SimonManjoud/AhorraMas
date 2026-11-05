package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.*;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre.ReporteGuardado;
import ve.impulsatech.ahorramas.cofre.dominio.EspejoImpacto;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;

/**
 * Engranaje 3 · Espejo de Impacto. Mes en curso: cálculo provisional. Mes cerrado: se calcula
 * una sola vez y queda en REPORTE_MENSUAL (inmutable).
 */
@Service
class ServicioEspejo implements ConsultarEspejo {

    private static final Logger log = LoggerFactory.getLogger(ServicioEspejo.class);

    private final RepositorioCofre repo;
    private final TasasApi tasas;
    private final CuentasApi cuentas;
    private final Reloj reloj;

    ServicioEspejo(RepositorioCofre repo, TasasApi tasas, CuentasApi cuentas, Reloj reloj) {
        this.repo = repo;
        this.tasas = tasas;
        this.cuentas = cuentas;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public VistaEspejo espejo(long idUsuario, Integer anio, Integer mes) {
        LocalDate hoy = reloj.hoy();
        YearMonth actual = YearMonth.from(hoy);
        YearMonth ym = anio == null || mes == null ? actual : YearMonth.of(anio, mes);
        if (ym.isAfter(actual)) {
            throw ErrorNegocio.validacion("No se puede calcular el Espejo de Impacto de un mes futuro");
        }
        Optional<ReporteGuardado> guardado = repo.reporteMensual(idUsuario, ym.getYear(), ym.getMonthValue());
        if (guardado.isPresent()) {
            return desdeGuardado(guardado.get());
        }
        boolean cerrado = ym.isBefore(actual);
        LocalDate corte = cerrado ? ym.atEndOfMonth() : hoy;
        EspejoImpacto.Resultado r = EspejoImpacto.calcular(repo.aportesDeUsuario(idUsuario), ym, corte, tasas::tasaDe);
        Integer inflacion = tasas.inflacionMensualPb(ym.getYear(), ym.getMonthValue())
                .or(() -> tasas.inflacionMensualPb(ym.minusMonths(1).getYear(), ym.minusMonths(1).getMonthValue()))
                .orElse(null);
        CuentasApi.Totales totales = cuentas.totales(idUsuario);
        if (cerrado) {
            repo.insertarReporteMensual(new ReporteGuardado(idUsuario, ym.getYear(), ym.getMonthValue(), totales.totalBsCts(),
                    totales.totalUsdCts(), r.saldoCofresUsdCts(), r.perdidaEvitadaBsCts(), r.tasaInicio().valorBsX10000(),
                    r.tasaFin().valorBsX10000(), inflacion, reloj.ahora()));
        }
        return new VistaEspejo(ym.getYear(), ym.getMonthValue(), !cerrado, r.saldoCofresUsdCts(), r.perdidaEvitadaBsCts(),
                r.perdidaEvitadaUsdCts(), tasa(r.tasaInicio()), tasa(r.tasaFin()), r.devaluacionPb(), inflacion, totales.totalBsCts(),
                totales.totalUsdCts(), r.frase(), EspejoImpacto.DISCLAIMER);
    }

    /** Cierre de mes (worker): genera el reporte una sola vez para cada usuario con metas. */
    void generarReportes(YearMonth mes) {
        for (Long idUsuario : repo.usuariosConMetas()) {
            try {
                espejo(idUsuario, mes.getYear(), mes.getMonthValue());
            } catch (RuntimeException e) {
                log.warn("No se pudo generar el Espejo de Impacto {} del usuario {}: {}", mes, idUsuario, e.getMessage());
            }
        }
    }

    private VistaEspejo desdeGuardado(ReporteGuardado g) {
        YearMonth ym = YearMonth.of(g.anio(), g.mes());
        TasaBcv ini = new TasaBcv(ym.atDay(1), g.tasaInicioBsX10000());
        TasaBcv fin = new TasaBcv(ym.atEndOfMonth(), g.tasaFinBsX10000());
        long devaluacion = fin.variacionPbDesde(ini);
        String frase = EspejoImpacto.frase(g.saldoCofresUsdCts(), g.perdidaEvitadaBsCts(), devaluacion);
        return new VistaEspejo(g.anio(), g.mes(), false, g.saldoCofresUsdCts(), g.perdidaEvitadaBsCts(), fin.bsAUsd(g.perdidaEvitadaBsCts()),
                tasa(ini), tasa(fin), devaluacion, g.inflacionMensualPb(), g.totalBsCts(), g.totalUsdCts(), frase, EspejoImpacto.DISCLAIMER);
    }

    private static TasaVista tasa(TasaBcv t) {
        return new TasaVista(t.fecha(), t.valorBsX10000());
    }
}
