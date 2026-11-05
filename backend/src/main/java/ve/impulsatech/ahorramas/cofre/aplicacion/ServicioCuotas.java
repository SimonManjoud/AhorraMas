package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.ConsultarRutaDelDia;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.VistaRuta;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre.CuotaGuardada;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.RutaDelDia;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;

/** Engranaje 1 · Ruta del día: una CUOTA_DIARIA por meta y periodo, a la tasa BCV del día (R-09). */
@Service
class ServicioCuotas implements ConsultarRutaDelDia {

    private final RepositorioCofre repo;
    private final TasasApi tasas;
    private final Reloj reloj;

    ServicioCuotas(RepositorioCofre repo, TasasApi tasas, Reloj reloj) {
        this.repo = repo;
        this.tasas = tasas;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public VistaRuta rutaDelDia(long idUsuario, long idMeta) {
        MetaAhorro meta = repo.meta(idMeta).filter(m -> m.idUsuario() == idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Meta"));
        if (!meta.activa()) {
            throw ErrorNegocio.regla("META_NO_ACTIVA", "La meta está " + meta.estatus());
        }
        return vista(meta, cuotaHoy(meta));
    }

    @Override
    @Transactional
    public List<VistaRuta> rutasDelDia(long idUsuario) {
        return repo.metas(idUsuario).stream().filter(MetaAhorro::activa).map(m -> vista(m, cuotaHoy(m))).toList();
    }

    /** Obtiene (o calcula y fija una sola vez) la cuota del periodo actual. */
    @Transactional
    CuotaGuardada cuotaHoy(MetaAhorro meta) {
        LocalDate hoy = reloj.hoy();
        LocalDate periodo = RutaDelDia.inicioPeriodo(hoy, meta.frecuenciaCuota());
        return repo.cuota(meta.idMeta(), periodo).orElseGet(() -> {
            long aportadoPeriodo = aportadoEnPeriodo(meta, periodo);
            long acumuladoAlInicio = repo.acumulado(meta.idMeta()) - aportadoPeriodo;
            RutaDelDia.Cuota c = RutaDelDia.calcular(meta, acumuladoAlInicio, hoy, tasas.tasaDe(hoy));
            return repo.insertarCuota(new CuotaGuardada(null, meta.idMeta(), c.fechaPeriodo(), c.montoUsdCts(), c.montoBsCts(),
                    c.tasaValorBsX10000(), c.periodosRestantes(), RutaDelDia.estatus(c.montoUsdCts(), aportadoPeriodo)));
        });
    }

    /** Recalcula el estatus de la cuota vigente tras un aporte. */
    @Transactional
    void actualizarEstatus(MetaAhorro meta) {
        CuotaGuardada c = cuotaHoy(meta);
        String nuevo = RutaDelDia.estatus(c.montoSugeridoUsdCts(), aportadoEnPeriodo(meta, c.fecha()));
        if (!nuevo.equals(c.estatus())) {
            repo.actualizarEstatusCuota(c.idCuota(), nuevo);
        }
    }

    long aportadoEnPeriodo(MetaAhorro meta, LocalDate inicioPeriodo) {
        var zona = reloj.zona();
        OffsetDateTime desde = inicioPeriodo.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime hasta = RutaDelDia.finPeriodo(inicioPeriodo, meta.frecuenciaCuota()).plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        return repo.aportadoEntre(meta.idMeta(), desde, hasta);
    }

    VistaRuta vista(MetaAhorro meta, CuotaGuardada c) {
        long aportado = aportadoEnPeriodo(meta, c.fecha());
        String estatus = "colchon".equals(c.estatus()) || "omitida".equals(c.estatus()) ? c.estatus()
                : RutaDelDia.estatus(c.montoSugeridoUsdCts(), aportado);
        return new VistaRuta(meta.idMeta(), meta.nombre(), c.fecha(), c.montoSugeridoUsdCts(), c.montoSugeridoBsCts(), c.tasaValorBsX10000(),
                aportado, estatus, c.periodosRestantes(),
                RutaDelDia.mensaje(meta.nombre(), c.montoSugeridoUsdCts(), c.montoSugeridoBsCts(), meta.frecuenciaCuota(), estatus));
    }

    /** Cuotas vencidas sin cumplir: "colchon" si ese día se usó un colchón, si no "omitida". */
    @Transactional
    void cerrarCuotasHasta(LocalDate fecha) {
        for (CuotaGuardada c : repo.cuotasPendientesHasta(fecha)) {
            MetaAhorro meta = repo.meta(c.idMeta()).orElseThrow();
            LocalDate fin = RutaDelDia.finPeriodo(c.fecha(), meta.frecuenciaCuota());
            if (fin.isAfter(fecha)) {
                continue;
            }
            boolean colchon = repo.diasRachaUsuario(meta.idUsuario(), fin, fin).stream().anyMatch(d -> "colchon".equals(d.tipo()));
            repo.actualizarEstatusCuota(c.idCuota(), colchon ? "colchon" : "omitida");
        }
    }
}
