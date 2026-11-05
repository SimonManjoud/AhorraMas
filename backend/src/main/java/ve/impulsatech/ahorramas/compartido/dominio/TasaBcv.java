package ve.impulsatech.ahorramas.compartido.dominio;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Tasa oficial BCV fechada: bolívares por 1 USD, expresada como entero × 10⁴ (R-01, R-07).
 *
 * <p>Regla de redondeo definida para todo el sistema: <b>mitad hacia arriba</b> (HALF_UP) al céntimo,
 * calculado con aritmética entera exacta (sin punto flotante).
 */
public record TasaBcv(LocalDate fecha, long valorBsX10000) {

    public static final long ESCALA = 10_000L;

    public TasaBcv {
        Objects.requireNonNull(fecha, "fecha");
        if (valorBsX10000 <= 0) {
            throw ErrorNegocio.validacion("La tasa BCV debe ser positiva");
        }
    }

    /** USD (céntimos) → Bs (céntimos). */
    public long usdABs(long usdCts) {
        return dividirRedondeando(BigInteger.valueOf(usdCts).multiply(BigInteger.valueOf(valorBsX10000)), BigInteger.valueOf(ESCALA));
    }

    /** Bs (céntimos) → USD (céntimos). */
    public long bsAUsd(long bsCts) {
        return dividirRedondeando(BigInteger.valueOf(bsCts).multiply(BigInteger.valueOf(ESCALA)), BigInteger.valueOf(valorBsX10000));
    }

    /** Convierte un monto en cualquier moneda a Bs. */
    public long aBs(long cts, Moneda moneda) {
        return moneda == Moneda.VES ? cts : usdABs(cts);
    }

    /** Convierte un monto en cualquier moneda a USD. */
    public long aUsd(long cts, Moneda moneda) {
        return moneda == Moneda.VES ? bsAUsd(cts) : cts;
    }

    /** Variación de esta tasa respecto a otra anterior, en puntos básicos (devaluación si es positiva). */
    public long variacionPbDesde(TasaBcv anterior) {
        return dividirRedondeando(
                BigInteger.valueOf(valorBsX10000 - anterior.valorBsX10000).multiply(BigInteger.valueOf(10_000)),
                BigInteger.valueOf(anterior.valorBsX10000));
    }

    /** División entera con redondeo HALF_UP (simétrico para negativos). */
    static long dividirRedondeando(BigInteger numerador, BigInteger divisor) {
        BigInteger[] qr = numerador.abs().divideAndRemainder(divisor.abs());
        BigInteger q = qr[0];
        if (qr[1].shiftLeft(1).compareTo(divisor.abs()) >= 0) {
            q = q.add(BigInteger.ONE);
        }
        boolean negativo = numerador.signum() * divisor.signum() < 0;
        return (negativo ? q.negate() : q).longValueExact();
    }
}
