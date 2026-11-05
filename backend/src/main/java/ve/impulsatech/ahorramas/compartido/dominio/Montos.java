package ve.impulsatech.ahorramas.compartido.dominio;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Utilidades puras para montos enteros en céntimos y puntos básicos. */
public final class Montos {

    private Montos() {
    }

    /** a × pb / 10 000 con redondeo HALF_UP. */
    public static long porcentajePb(long montoCts, long pb) {
        return TasaBcv.dividirRedondeando(
                java.math.BigInteger.valueOf(montoCts).multiply(java.math.BigInteger.valueOf(pb)),
                java.math.BigInteger.valueOf(10_000));
    }

    /** División entera redondeando hacia arriba (para cuotas: nunca quedarse corto). */
    public static long dividirHaciaArriba(long a, long b) {
        if (b <= 0) {
            throw ErrorNegocio.validacion("Divisor inválido");
        }
        return Math.floorDiv(a + b - 1, b);
    }

    public static void exigirPositivo(long cts, String campo) {
        if (cts <= 0) {
            throw ErrorNegocio.validacion("El campo " + campo + " debe ser un monto positivo en céntimos");
        }
    }

    /** Formato para mensajes al usuario: "Bs 1.234,56" o "$12,34". */
    public static String formatear(long cts, Moneda moneda) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-VE")));
        String cuerpo = f.format(java.math.BigDecimal.valueOf(cts, 2));
        return switch (moneda) {
            case VES -> "Bs " + cuerpo;
            case USD -> "$" + cuerpo;
            case USDT -> cuerpo + " USDT";
        };
    }
}
