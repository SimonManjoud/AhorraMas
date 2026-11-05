package ve.impulsatech.ahorramas.cofre.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** META_AHORRO (el Cofre): meta emocional con plazo y monto en USD (R-08). */
public record MetaAhorro(
        Long idMeta,
        long idUsuario,
        String nombre,
        String categoria,
        long montoObjetivoUsdCts,
        LocalDate fechaObjetivo,
        String frecuenciaCuota,
        OffsetDateTime fechaCreacion,
        String estatus,
        OffsetDateTime fechaCierre) {

    public static final Set<String> CATEGORIAS = Set.of("viaje", "emergencia", "estudio", "salud", "hogar", "otro");
    public static final Set<String> FRECUENCIAS = Set.of("diaria", "semanal");
    public static final long MAXIMO_USD_CTS = 100_000_000L; // $1.000.000

    public static MetaAhorro crear(long idUsuario, String nombre, String categoria, long montoObjetivoUsdCts, LocalDate fechaObjetivo,
                                   String frecuencia, OffsetDateTime ahora) {
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 60) {
            throw ErrorNegocio.validacion("Ponle un nombre a tu meta (máximo 60 caracteres)");
        }
        if (!CATEGORIAS.contains(categoria)) {
            throw ErrorNegocio.validacion("Categoría inválida");
        }
        if (montoObjetivoUsdCts <= 0 || montoObjetivoUsdCts > MAXIMO_USD_CTS) {
            throw ErrorNegocio.validacion("El monto objetivo debe ser positivo (en céntimos de USD)");
        }
        if (fechaObjetivo == null || !fechaObjetivo.isAfter(ahora.toLocalDate())) {
            throw ErrorNegocio.regla("R-08", "La fecha objetivo debe ser posterior a hoy");
        }
        String f = frecuencia == null ? "diaria" : frecuencia;
        if (!FRECUENCIAS.contains(f)) {
            throw ErrorNegocio.validacion("La frecuencia de la cuota debe ser diaria o semanal");
        }
        return new MetaAhorro(null, idUsuario, nombre.trim(), categoria, montoObjetivoUsdCts, fechaObjetivo, f, ahora, "activa", null);
    }

    public boolean activa() {
        return "activa".equals(estatus);
    }

    public boolean abierta() {
        return "activa".equals(estatus) || "pausada".equals(estatus);
    }

    /** Transiciones permitidas por el usuario: activa ↔ pausada, y cancelar mientras esté abierta. */
    public MetaAhorro cambiarEstatus(String nuevo, OffsetDateTime ahora) {
        if (!abierta()) {
            throw ErrorNegocio.regla("META_CERRADA", "La meta ya está " + estatus);
        }
        return switch (nuevo) {
            case "activa", "pausada" -> new MetaAhorro(idMeta, idUsuario, nombre, categoria, montoObjetivoUsdCts, fechaObjetivo,
                    frecuenciaCuota, fechaCreacion, nuevo, null);
            case "cancelada" -> cerrar("cancelada", ahora);
            default -> throw ErrorNegocio.validacion("Estatus no permitido: " + nuevo);
        };
    }

    public MetaAhorro cerrar(String estatusFinal, OffsetDateTime ahora) {
        return new MetaAhorro(idMeta, idUsuario, nombre, categoria, montoObjetivoUsdCts, fechaObjetivo, frecuenciaCuota, fechaCreacion,
                estatusFinal, ahora);
    }

    /** Progreso en puntos básicos (10 000 = 100 %), tope 100 %. */
    public int progresoPb(long acumuladoUsdCts) {
        if (acumuladoUsdCts <= 0) {
            return 0;
        }
        return (int) Math.min(10_000, acumuladoUsdCts * 10_000 / montoObjetivoUsdCts);
    }
}
