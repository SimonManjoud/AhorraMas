package ve.impulsatech.ahorramas.simuladores.bcv;

/** DTOs del simulador BCV (contrato §1). */
public final class BcvDtos {

    private BcvDtos() {
    }

    /** Tasa oficial: {@code valorBsX10000} = Bs por 1 USD × 10⁴. */
    public record Tasa(String moneda, String fecha, long valorBsX10000, String fuente,
                       String fechaHoraPublicacion) {
    }

    /** Inflación mensual en puntos básicos (1 240 pb = 12,40 %). */
    public record Inflacion(int anio, int mes, long variacionMensualPb, String fechaPublicacion,
                            String fuente) {
    }

    /** Control demo: fuerza la tasa del día. */
    public record FijarTasa(Long valorBsX10000) {
    }
}
