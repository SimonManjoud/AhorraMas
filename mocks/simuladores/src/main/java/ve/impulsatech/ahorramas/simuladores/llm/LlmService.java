package ve.impulsatech.ahorramas.simuladores.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;
import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.llm.LlmController.Explicacion;

/**
 * LLM simulado: genera una explicación determinista en español simple a partir del perfil
 * seudonimizado y aplica una guardia que rechaza cualquier cosa que parezca PII.
 */
@Service
public class LlmService {

    static final String MODELO = "sim-llm-1.0";
    private static final List<String> CLAVES_PII = List.of("cedula", "nombre", "correo", "telefono");
    /** 7 o más dígitos seguidos contienen una secuencia de 7–9 dígitos (cédula, teléfono...). */
    private static final Pattern DIGITOS = Pattern.compile("\\d{7,}");

    private final JsonMapper json = JsonMapper.builder().build();

    public Explicacion explicar(String cuerpo) {
        Object raiz;
        try {
            raiz = json.readValue(cuerpo == null ? "" : cuerpo, Object.class);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "JSON_INVALIDO", "El cuerpo no es un JSON válido");
        }
        if (!(raiz instanceof Map<?, ?> solicitud)) {
            throw ApiException.solicitudInvalida("Se esperaba un objeto JSON");
        }
        String pii = buscarPii(solicitud, true);
        if (pii == null && cuerpo.contains("@")) {
            pii = "carácter '@' (posible correo)";
        }
        if (pii != null) {
            throw new ApiException(HttpStatus.valueOf(422), "PII_DETECTADA",
                    "La solicitud contiene datos que parecen personales: " + pii
                            + ". Solo se aceptan datos seudonimizados.");
        }
        return new Explicacion(redactar(solicitud), MODELO);
    }

    // ---------------------------------------------------------------- guardia de PII

    private static String buscarPii(Object nodo, boolean raiz) {
        if (nodo instanceof Map<?, ?> mapa) {
            for (Map.Entry<?, ?> e : mapa.entrySet()) {
                String clave = String.valueOf(e.getKey());
                String normal = sinAcentos(clave.toLowerCase(Locale.ROOT));
                for (String prohibida : CLAVES_PII) {
                    if (normal.contains(prohibida)) {
                        return "clave '" + clave + "'";
                    }
                }
                if (DIGITOS.matcher(clave).find()) {
                    return "secuencia numérica en la clave '" + clave + "'";
                }
                if (raiz && clave.equals("idSeudonimo")) {
                    continue; // el seudónimo es el único número largo permitido
                }
                String encontrado = buscarPii(e.getValue(), false);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        } else if (nodo instanceof List<?> lista) {
            for (Object o : lista) {
                String encontrado = buscarPii(o, false);
                if (encontrado != null) {
                    return encontrado;
                }
            }
        } else if (nodo != null && DIGITOS.matcher(String.valueOf(nodo)).find()) {
            return "secuencia de 7 a 9 dígitos";
        }
        return null;
    }

    private static String sinAcentos(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // ---------------------------------------------------------------- redacción

    private static String redactar(Map<?, ?> s) {
        String perfil = texto(s.get("perfil"), "moderado");
        Map<?, ?> rasgos = s.get("rasgos") instanceof Map<?, ?> m ? m : Map.of();
        String objetivo = texto(rasgos.get("objetivoPrincipal"), "ahorrar");
        long horizonte = numero(rasgos.get("horizonteMeses"), 6);
        String tolerancia = texto(rasgos.get("toleranciaRiesgo"), "media");
        long volatilidadPb = numero(rasgos.get("volatilidadSaldoPb"), 0);
        long metas = numero(rasgos.get("metasActivas"), 0);

        List<String> oraciones = new ArrayList<>();
        oraciones.add(switch (perfil) {
            case "conservador" -> "Tu perfil es conservador: priorizas cuidar lo que ya tienes antes que buscar más rendimiento.";
            case "dinamico" -> "Tu perfil es dinámico: aceptas más movimiento en tu ahorro a cambio de mayor potencial de crecimiento.";
            default -> "Tu perfil es moderado: buscas un equilibrio entre proteger tu dinero y hacerlo crecer.";
        });
        String distribucion = describirDistribucion(s.get("distribucion"));
        if (distribucion != null) {
            oraciones.add("Por eso la distribución sugerida es " + distribucion + ".");
        }
        String razonObjetivo = switch (objetivo) {
            case "proteger" -> "Como tu objetivo principal es proteger tu dinero, la mayor parte va a divisas a tasa BCV, "
                    + "que suelen perder menos valor que el bolívar frente a la inflación";
            case "crecer" -> "Como tu objetivo principal es hacer crecer tu dinero, se da algo más de peso a instrumentos con mayor potencial";
            default -> "La sugerencia busca que tu ahorro mantenga su poder de compra";
        };
        oraciones.add(razonObjetivo + ", pensando en un horizonte de " + horizonte
                + (horizonte == 1 ? " mes." : " meses."));
        if (volatilidadPb >= 3000) {
            oraciones.add("Tus saldos varían bastante de un mes a otro, así que conviene mantener una parte disponible "
                    + "en bolívares para los gastos del día a día.");
        } else if (metas > 0) {
            oraciones.add("Tienes " + metas + (metas == 1 ? " meta activa" : " metas activas")
                    + "; apartar pequeñas cuotas con constancia te acerca más que aportes grandes y esporádicos.");
        } else {
            oraciones.add("Con una tolerancia al riesgo " + tolerancia
                    + ", lo importante es la constancia: aportes pequeños y frecuentes.");
        }
        oraciones.add("Esto es una simulación informativa, no una asesoría financiera.");
        return String.join(" ", oraciones);
    }

    private static String describirDistribucion(Object distribucion) {
        if (!(distribucion instanceof List<?> lista) || lista.isEmpty()) {
            return null;
        }
        List<String> partes = new ArrayList<>();
        for (Object o : lista) {
            if (o instanceof Map<?, ?> item) {
                long pb = numero(item.get("porcentajePb"), 0);
                String porcentaje = pb % 100 == 0 ? String.valueOf(pb / 100)
                        : String.format(Locale.ROOT, "%d,%02d", pb / 100, pb % 100);
                Object instrumento = item.get("instrumento");
                partes.add(porcentaje + " % en " + (instrumento == null ? "otros" : instrumento));
            }
        }
        if (partes.isEmpty()) {
            return null;
        }
        if (partes.size() == 1) {
            return partes.getFirst();
        }
        return String.join(", ", partes.subList(0, partes.size() - 1)) + " y " + partes.getLast();
    }

    private static String texto(Object valor, String porDefecto) {
        return valor == null ? porDefecto : String.valueOf(valor).toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
    }

    private static long numero(Object valor, long porDefecto) {
        return valor instanceof Number n ? n.longValue() : porDefecto;
    }
}
