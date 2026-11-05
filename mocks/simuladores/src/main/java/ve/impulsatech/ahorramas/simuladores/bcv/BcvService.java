package ve.impulsatech.ahorramas.simuladores.bcv;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ve.impulsatech.ahorramas.simuladores.bcv.BcvDtos.Inflacion;
import ve.impulsatech.ahorramas.simuladores.bcv.BcvDtos.Tasa;
import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.comun.Semilla;
import ve.impulsatech.ahorramas.simuladores.comun.Tiempo;

/**
 * Serie determinista de la tasa oficial y de la inflación mensual.
 * <ul>
 *   <li>Base: 301,37 Bs/USD el 2025-12-31 ({@value #VALOR_BASE}).</li>
 *   <li>Devaluación compuesta de 0,18 % por día calendario, con ruido determinista por fecha de
 *       ±0,08 % (la serie es casi monótona).</li>
 *   <li>Sábado y domingo repiten la tasa del viernes.</li>
 * </ul>
 */
@Service
public class BcvService {

    static final long VALOR_BASE = 3_013_700L;
    static final LocalDate FECHA_BASE = LocalDate.of(2025, 12, 31);
    private static final BigDecimal FACTOR_DIARIO = new BigDecimal("1.0018");
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int MAX_DIAS_RANGO = 3_700;
    private static final String FUENTE = "BCV";

    private final Tiempo tiempo;
    /** Tasas forzadas con el control demo (fecha → valor). */
    private final Map<LocalDate, Long> tasasForzadas = new ConcurrentHashMap<>();

    public BcvService(Tiempo tiempo) {
        this.tiempo = tiempo;
    }

    public Tasa tasaHoy(String moneda) {
        return tasa(validarMoneda(moneda), tiempo.hoy());
    }

    public List<Tasa> serie(String moneda, LocalDate desde, LocalDate hasta) {
        String m = validarMoneda(moneda);
        LocalDate hoy = tiempo.hoy();
        LocalDate fin = (hasta == null || hasta.isAfter(hoy)) ? hoy : hasta;
        if (desde.isAfter(fin)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGO_INVALIDO",
                    "'desde' debe ser anterior o igual a 'hasta' (y no posterior a hoy)");
        }
        if (ChronoUnit.DAYS.between(desde, fin) > MAX_DIAS_RANGO) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGO_INVALIDO", "El rango máximo es de 10 años");
        }
        List<Tasa> tasas = new ArrayList<>();
        for (LocalDate d = desde; !d.isAfter(fin); d = d.plusDays(1)) {
            tasas.add(tasa(m, d));
        }
        return tasas;
    }

    public Tasa fijarTasaHoy(Long valor) {
        if (valor == null || valor <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALOR_INVALIDO",
                    "valorBsX10000 debe ser un entero positivo");
        }
        tasasForzadas.put(tiempo.hoy(), valor);
        return tasaHoy("USD");
    }

    public Tasa quitarTasaForzada() {
        tasasForzadas.remove(tiempo.hoy());
        return tasaHoy("USD");
    }

    public boolean hayTasaForzadaHoy() {
        return tasasForzadas.containsKey(tiempo.hoy());
    }

    public Inflacion inflacion(int anio, int mes) {
        if (mes < 1 || mes > 12) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PARAMETRO_INVALIDO", "'mes' debe estar entre 1 y 12");
        }
        Inflacion inf = generarInflacion(YearMonth.of(anio, mes));
        if (!publicada(inf)) {
            throw ApiException.noEncontrado("INFLACION_NO_PUBLICADA",
                    "La inflación de " + YearMonth.of(anio, mes) + " aún no ha sido publicada");
        }
        return inf;
    }

    public List<Inflacion> inflacionDesde(YearMonth desde, YearMonth hasta) {
        YearMonth fin = YearMonth.from(tiempo.hoy());
        if (hasta != null && hasta.isBefore(fin)) {
            fin = hasta;
        }
        if (desde.isBefore(YearMonth.of(2000, 1))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "RANGO_INVALIDO", "'desde' debe ser 2000-01 o posterior");
        }
        List<Inflacion> lista = new ArrayList<>();
        for (YearMonth ym = desde; !ym.isAfter(fin); ym = ym.plusMonths(1)) {
            Inflacion inf = generarInflacion(ym);
            if (publicada(inf)) {
                lista.add(inf);
            }
        }
        return lista;
    }

    // ---------------------------------------------------------------- generación

    Tasa tasa(String moneda, LocalDate fecha) {
        LocalDate habil = diaHabil(fecha);
        Long forzada = tasasForzadas.get(fecha);
        LocalDate publicacion = forzada != null ? fecha : habil;
        if (forzada == null) {
            forzada = tasasForzadas.get(habil); // fin de semana tras un viernes forzado
        }
        long valor = forzada != null ? forzada : valorGenerado(habil);
        return new Tasa(moneda, fecha.toString(), valor, FUENTE,
                Tiempo.formatear(Tiempo.en(publicacion, 8, 0)));
    }

    /** Sábado y domingo usan el viernes anterior. */
    static LocalDate diaHabil(LocalDate fecha) {
        return switch (fecha.getDayOfWeek()) {
            case SATURDAY -> fecha.minusDays(1);
            case SUNDAY -> fecha.minusDays(2);
            default -> fecha;
        };
    }

    static long valorGenerado(LocalDate fechaHabil) {
        long dias = ChronoUnit.DAYS.between(FECHA_BASE, fechaHabil);
        if (dias == 0) {
            return VALOR_BASE;
        }
        BigDecimal factor = FACTOR_DIARIO.pow((int) dias, MC);
        // Ruido determinista en [-0,08 %, +0,08 %]
        long r = Math.floorMod(Semilla.de("bcv", fechaHabil), 161L) - 80;
        BigDecimal ruido = BigDecimal.ONE.add(BigDecimal.valueOf(r).movePointLeft(5));
        return BigDecimal.valueOf(VALOR_BASE).multiply(factor, MC).multiply(ruido, MC)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static Inflacion generarInflacion(YearMonth ym) {
        long pb = 800 + Math.floorMod(Semilla.de("inflacion", ym), 1001L);
        LocalDate publicacion = ym.plusMonths(1).atDay(3);
        return new Inflacion(ym.getYear(), ym.getMonthValue(), pb, publicacion.toString(), FUENTE);
    }

    /** Un mes se publica el día 3 del mes siguiente; el mes en curso nunca está publicado. */
    private boolean publicada(Inflacion inf) {
        return !LocalDate.parse(inf.fechaPublicacion()).isAfter(tiempo.hoy());
    }

    private static String validarMoneda(String moneda) {
        String m = moneda == null ? "USD" : moneda.trim().toUpperCase(Locale.ROOT);
        if (!m.equals("USD") && !m.equals("USDT")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MONEDA_NO_SOPORTADA", "Monedas soportadas: USD, USDT");
        }
        return m;
    }
}
