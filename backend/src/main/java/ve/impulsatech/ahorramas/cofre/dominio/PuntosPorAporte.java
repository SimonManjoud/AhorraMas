package ve.impulsatech.ahorramas.cofre.dominio;

/**
 * Puntos Ahorro que genera un aporte: 10 puntos base por el primer aporte del día + 1 punto por
 * cada dólar, multiplicados por el tramo de racha vigente. El bono de racha se informa aparte.
 */
public final class PuntosPorAporte {

    public static final int BASE_DIARIA = 10;

    private PuntosPorAporte() {
    }

    public record Puntos(long base, long bonoRacha) {
        public long total() {
            return base + bonoRacha;
        }
    }

    public static Puntos calcular(long montoUsdCts, boolean primerAporteDelDia, int multiplicadorX100) {
        long base = (primerAporteDelDia ? BASE_DIARIA : 0) + montoUsdCts / 100;
        long total = base * multiplicadorX100 / 100;
        return new Puntos(base, Math.max(0, total - base));
    }
}
