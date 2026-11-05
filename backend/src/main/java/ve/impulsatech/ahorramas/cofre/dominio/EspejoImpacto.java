package ve.impulsatech.ahorramas.cofre.dominio;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.function.Function;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;

/**
 * Engranaje 3 · Espejo de Impacto: poder de compra que el cofre en divisas salvó en el mes frente
 * a haber dejado el mismo dinero en bolívares.
 *
 * <pre>
 * pérdida evitada (Bs) = Σ usdᵢ × (tasaFin − tasaRefᵢ)
 *   tasaRefᵢ = tasa BCV del día del asiento, o la del 1.º del mes si el asiento es anterior.
 *   (los retiros entran con signo negativo: dejan de estar protegidos desde ese día)
 * </pre>
 * Es una simulación informativa: no mueve dinero ni pide claves; solo lee el ledger y la tasa BCV.
 */
public final class EspejoImpacto {

    public static final String DISCLAIMER =
            "Simulación informativa basada en la tasa oficial BCV; no constituye asesoría financiera.";

    private EspejoImpacto() {
    }

    public record Resultado(YearMonth mes, long saldoCofresUsdCts, long perdidaEvitadaBsCts, long perdidaEvitadaUsdCts,
                            TasaBcv tasaInicio, TasaBcv tasaFin, long devaluacionPb, String frase) {
    }

    /**
     * @param asientos   todos los asientos del ledger de los cofres del usuario
     * @param mes        mes a evaluar
     * @param fechaCorte último día considerado (fin de mes u hoy si el mes está en curso)
     * @param tasaDe     tasa BCV vigente en una fecha
     */
    public static Resultado calcular(Collection<Aporte> asientos, YearMonth mes, LocalDate fechaCorte, Function<LocalDate, TasaBcv> tasaDe) {
        LocalDate inicio = mes.atDay(1);
        TasaBcv tasaInicio = tasaDe.apply(inicio);
        TasaBcv tasaFin = tasaDe.apply(fechaCorte);

        BigInteger acumuladoBsX10000 = BigInteger.ZERO;
        long saldo = 0;
        for (Aporte a : asientos) {
            LocalDate fecha = a.fechaHora().toLocalDate();
            if (fecha.isAfter(fechaCorte)) {
                continue;
            }
            saldo += a.montoConSigno();
            long tasaRef = fecha.isBefore(inicio) ? tasaInicio.valorBsX10000() : tasaDe.apply(fecha).valorBsX10000();
            acumuladoBsX10000 = acumuladoBsX10000.add(BigInteger.valueOf(a.montoConSigno()).multiply(BigInteger.valueOf(tasaFin.valorBsX10000() - tasaRef)));
        }
        long perdidaBs = redondear(acumuladoBsX10000);
        long perdidaUsd = tasaFin.bsAUsd(perdidaBs);
        long devaluacion = tasaFin.variacionPbDesde(tasaInicio);
        return new Resultado(mes, saldo, perdidaBs, perdidaUsd, tasaInicio, tasaFin, devaluacion, frase(saldo, perdidaBs, devaluacion));
    }

    public static String frase(long saldoUsd, long perdidaBs, long devaluacionPb) {
        if (saldoUsd <= 0) {
            return "Aún no tienes dinero en tu cofre: cada dólar que apartes queda protegido de la devaluación.";
        }
        if (perdidaBs > 0) {
            return "Tu cofre de " + Montos.formatear(saldoUsd, Moneda.USD) + " te protegió de perder "
                    + Montos.formatear(perdidaBs, Moneda.VES) + " este mes.";
        }
        if (devaluacionPb < 0) {
            return "Este mes el bolívar se apreció: tu cofre de " + Montos.formatear(saldoUsd, Moneda.USD) + " conserva su valor en dólares.";
        }
        return "Tu cofre de " + Montos.formatear(saldoUsd, Moneda.USD) + " mantuvo su valor este mes.";
    }

    private static long redondear(BigInteger valorX10000) {
        BigInteger diez = BigInteger.valueOf(TasaBcv.ESCALA);
        BigInteger[] qr = valorX10000.abs().divideAndRemainder(diez);
        BigInteger q = qr[1].shiftLeft(1).compareTo(diez) >= 0 ? qr[0].add(BigInteger.ONE) : qr[0];
        return (valorX10000.signum() < 0 ? q.negate() : q).longValueExact();
    }
}
