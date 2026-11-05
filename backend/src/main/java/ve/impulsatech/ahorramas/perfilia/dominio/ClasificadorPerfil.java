package ve.impulsatech.ahorramas.perfilia.dominio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/**
 * Reglas versionadas ({@value #VERSION_REGLAS}) del perfil de riesgo. Entradas seudonimizadas: rango de
 * ingreso, volatilidad del saldo, objetivo, horizonte y tolerancia. Sin nombre, cédula ni cuentas.
 */
public final class ClasificadorPerfil {

    public static final String VERSION_REGLAS = "reglas-1.2";
    public static final Set<String> OBJETIVOS = Set.of("proteger", "meta_corta", "crecer");
    public static final Set<String> TOLERANCIAS = Set.of("baja", "media", "alta");

    private ClasificadorPerfil() {
    }

    public record Rasgos(String objetivoPrincipal, int horizonteMeses, String toleranciaRiesgo, String rangoIngreso, int volatilidadSaldoPb,
                         int metasActivas) {
        public Rasgos {
            if (!OBJETIVOS.contains(objetivoPrincipal)) {
                throw ErrorNegocio.validacion("Objetivo principal inválido");
            }
            if (!TOLERANCIAS.contains(toleranciaRiesgo)) {
                throw ErrorNegocio.validacion("Tolerancia al riesgo inválida");
            }
            if (horizonteMeses < 1 || horizonteMeses > 120) {
                throw ErrorNegocio.validacion("El horizonte debe estar entre 1 y 120 meses");
            }
        }
    }

    public record ReglaSugerida(String tipo, long valor) {
    }

    public record Clasificacion(String perfil, int puntaje, Map<String, Integer> distribucionPb, ReglaSugerida reglaSugerida) {
    }

    /** Rango de ingreso mensual (USD) — se comparte el rango, nunca el monto. */
    public static String rangoIngreso(long ingresoMensualUsdCts) {
        if (ingresoMensualUsdCts < 10_000) {
            return "R1";
        }
        if (ingresoMensualUsdCts < 30_000) {
            return "R2";
        }
        if (ingresoMensualUsdCts < 70_000) {
            return "R3";
        }
        return ingresoMensualUsdCts < 150_000 ? "R4" : "R5";
    }

    public static Clasificacion clasificar(Rasgos r) {
        int puntaje = switch (r.objetivoPrincipal()) {
            case "proteger" -> 0;
            case "meta_corta" -> 1;
            default -> 2;
        };
        puntaje += r.horizonteMeses() <= 6 ? 0 : r.horizonteMeses() <= 24 ? 1 : 2;
        puntaje += switch (r.toleranciaRiesgo()) {
            case "baja" -> 0;
            case "media" -> 1;
            default -> 2;
        };
        puntaje += switch (r.rangoIngreso()) {
            case "R1", "R2" -> -1;
            case "R4", "R5" -> 1;
            default -> 0;
        };
        if (r.volatilidadSaldoPb() >= 3_000) {
            puntaje -= 1;   // ingresos muy irregulares: priorizar liquidez y protección
        }
        String perfil = puntaje <= 2 ? "conservador" : puntaje <= 4 ? "moderado" : "dinamico";
        Map<String, Integer> dist = new LinkedHashMap<>();
        switch (perfil) {
            case "conservador" -> { dist.put("Bs", 1000); dist.put("USD", 6000); dist.put("stablecoin", 1000); dist.put("plazo fijo", 2000); }
            case "moderado" -> { dist.put("Bs", 1000); dist.put("USD", 4500); dist.put("stablecoin", 2500); dist.put("plazo fijo", 2000); }
            default -> { dist.put("Bs", 500); dist.put("USD", 3500); dist.put("stablecoin", 4500); dist.put("plazo fijo", 1500); }
        }
        long pct = switch (perfil) {
            case "conservador" -> 800;
            case "moderado" -> 1200;
            default -> 1500;
        };
        return new Clasificacion(perfil, puntaje, dist, new ReglaSugerida("pct_ingreso", pct));
    }

    /** Explicación de respaldo si el LLM no está disponible (misma información, sin redacción generativa). */
    public static String explicacionRespaldo(Clasificacion c, Rasgos r) {
        return "Tu perfil es " + c.perfil() + " porque tu objetivo es «" + r.objetivoPrincipal().replace('_', ' ') + "» a "
                + r.horizonteMeses() + " meses con tolerancia " + r.toleranciaRiesgo() + ". Sugerimos mantener la mayor parte en dólares "
                + "para protegerte de la devaluación y apartar " + (c.reglaSugerida().valor() / 100.0) + " % de cada ingreso.";
    }
}
