package ve.impulsatech.ahorramas.consolidacion.aplicacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.Bcv;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.Repositorio;

/**
 * Tasa BCV fechada (R-07): se obtiene del BCV una vez y se guarda; toda conversión usa la tasa
 * vigente el día de la operación. Si el BCV no responde se usa la última tasa publicada.
 */
@Service
class ServicioTasas implements TasasApi {

    private static final Logger log = LoggerFactory.getLogger(ServicioTasas.class);

    private final Repositorio repo;
    private final Bcv bcv;
    private final Reloj reloj;
    private final Map<LocalDate, Boolean> consultadas = new ConcurrentHashMap<>();

    private final TransactionTemplate nuevaTx;

    ServicioTasas(Repositorio repo, Bcv bcv, Reloj reloj, PlatformTransactionManager tm) {
        this.repo = repo;
        this.bcv = bcv;
        this.reloj = reloj;
        // Lo obtenido del BCV se guarda en su propia transacción: funciona aunque el llamador sea de solo lectura.
        this.nuevaTx = new TransactionTemplate(tm);
        this.nuevaTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private void guardar(List<TasaBcv> tasas) {
        nuevaTx.executeWithoutResult(s -> tasas.forEach(t -> repo.guardarTasa(t, reloj.ahora())));
    }

    @Override
    public TasaBcv tasaDe(LocalDate fecha) {
        Optional<TasaBcv> guardada = repo.tasaGuardada(fecha);
        if (guardada.isPresent()) {
            return guardada.get();
        }
        if (consultadas.putIfAbsent(fecha, Boolean.TRUE) == null) {
            try {
                guardar(bcv.tasas(fecha.minusDays(10), fecha));
                if (repo.tasaGuardada(fecha).isEmpty() && !fecha.isBefore(reloj.hoy())) {
                    guardar(List.of(bcv.tasaHoy()));
                }
            } catch (RuntimeException e) {
                consultadas.remove(fecha);
                log.warn("No se pudo consultar el BCV para {}: {}", fecha, e.getMessage());
            }
        }
        return repo.tasaGuardada(fecha)
                .or(() -> repo.ultimaTasaHasta(fecha))
                .orElseThrow(() -> ErrorNegocio.servicioExterno("No hay tasa BCV disponible para " + fecha));
    }

    @Override
    public TasaBcv tasaHoy() {
        return tasaDe(reloj.hoy());
    }

    @Override
    public List<TasaBcv> serie(LocalDate desde, LocalDate hasta) {
        long dias = hasta.toEpochDay() - desde.toEpochDay() + 1;
        List<TasaBcv> guardadas = repo.tasasGuardadas(desde, hasta);
        if (guardadas.size() < dias) {
            try {
                guardar(bcv.tasas(desde, hasta));
                guardadas = repo.tasasGuardadas(desde, hasta);
            } catch (RuntimeException e) {
                log.warn("No se pudo obtener la serie BCV: {}", e.getMessage());
            }
        }
        return guardadas;
    }

    @Override
    public Optional<Integer> inflacionMensualPb(int anio, int mes) {
        Optional<Integer> guardada = repo.inflacionGuardada(anio, mes);
        if (guardada.isPresent()) {
            return guardada;
        }
        try {
            Optional<Integer> pb = bcv.inflacionPb(anio, mes);
            pb.ifPresent(v -> nuevaTx.executeWithoutResult(s -> repo.guardarInflacion(anio, mes, v, reloj.hoy())));
            return pb;
        } catch (RuntimeException e) {
            log.warn("No se pudo consultar la inflación {}-{}: {}", anio, mes, e.getMessage());
            return Optional.empty();
        }
    }
}
