package ve.impulsatech.ahorramas.cofre.dominio;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;

/**
 * Engranaje 1 · Ruta del día (R-09): convierte una meta lejana en una micro-acción.
 * <pre>cuota = ⌈(objetivo − acumulado) / periodos restantes⌉   (USD, céntimos)
 * cuotaBs = cuota × tasa BCV del día</pre>
 */
public final class RutaDelDia {

    private RutaDelDia() {
    }

    public record Cuota(LocalDate fechaPeriodo, int periodosRestantes, long montoUsdCts, long montoBsCts, long tasaValorBsX10000) {
    }

    /** Fecha que identifica el periodo: el día (diaria) o el lunes de la semana (semanal). */
    public static LocalDate inicioPeriodo(LocalDate hoy, String frecuencia) {
        return "semanal".equals(frecuencia) ? hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : hoy;
    }

    public static LocalDate finPeriodo(LocalDate inicio, String frecuencia) {
        return "semanal".equals(frecuencia) ? inicio.plusDays(6) : inicio;
    }

    /** Periodos que quedan desde hoy (incluido) hasta la víspera de la fecha objetivo; mínimo 1. */
    public static int periodosRestantes(LocalDate hoy, LocalDate fechaObjetivo, String frecuencia) {
        long dias = ChronoUnit.DAYS.between(hoy, fechaObjetivo);
        if (dias <= 0) {
            return 1;
        }
        long periodos = "semanal".equals(frecuencia) ? Montos.dividirHaciaArriba(dias, 7) : dias;
        return (int) Math.max(1, periodos);
    }

    public static Cuota calcular(MetaAhorro meta, long acumuladoAlInicioUsdCts, LocalDate hoy, TasaBcv tasa) {
        int periodos = periodosRestantes(hoy, meta.fechaObjetivo(), meta.frecuenciaCuota());
        long faltante = Math.max(0, meta.montoObjetivoUsdCts() - acumuladoAlInicioUsdCts);
        long cuotaUsd = faltante == 0 ? 0 : Montos.dividirHaciaArriba(faltante, periodos);
        return new Cuota(inicioPeriodo(hoy, meta.frecuenciaCuota()), periodos, cuotaUsd, tasa.usdABs(cuotaUsd), tasa.valorBsX10000());
    }

    /** Estatus de la cuota según lo aportado en el periodo. */
    public static String estatus(long sugeridoUsdCts, long aportadoUsdCts) {
        if (sugeridoUsdCts == 0 || aportadoUsdCts >= sugeridoUsdCts) {
            return "cumplida";
        }
        return aportadoUsdCts > 0 ? "parcial" : "pendiente";
    }

    public static String mensaje(String nombreMeta, long cuotaUsd, long cuotaBs, String frecuencia, String estatus) {
        if (cuotaUsd == 0) {
            return "¡Ya alcanzaste el monto de «" + nombreMeta + "»! Lo que apartes ahora es ganancia extra.";
        }
        String cuando = "semanal".equals(frecuencia) ? "Esta semana toca apartar " : "Hoy toca apartar ";
        String base = cuando + Montos.formatear(cuotaUsd, Moneda.USD) + " (" + Montos.formatear(cuotaBs, Moneda.VES) + ") para «" + nombreMeta + "»";
        return switch (estatus) {
            case "cumplida" -> "¡Cuota cumplida! " + base.replaceFirst("toca apartar", "apartaste");
            case "parcial" -> base + ". ¡Vas bien, ya aportaste una parte!";
            default -> base;
        };
    }
}
