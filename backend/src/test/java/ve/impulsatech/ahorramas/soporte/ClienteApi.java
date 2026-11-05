package ve.impulsatech.ahorramas.soporte;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP mínimo para las pruebas de integración: devuelve status + JSON sin lanzar en 4xx/5xx. */
public class ClienteApi {

    private final RestClient http;
    private final JsonMapper json;
    private String token;
    private String stepUp;
    private final Map<String, String> cabeceras = new LinkedHashMap<>();

    public ClienteApi(String base, JsonMapper json) {
        this.http = RestClient.builder().baseUrl(base).build();
        this.json = json;
    }

    public record Respuesta(int status, Object cuerpo) {
        @SuppressWarnings("unchecked")
        public Map<String, Object> mapa() {
            return (Map<String, Object>) cuerpo;
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> lista() {
            return (List<Map<String, Object>>) cuerpo;
        }

        public Object get(String ruta) {
            Object actual = cuerpo;
            for (String parte : ruta.split("\\.")) {
                actual = actual instanceof Map<?, ?> m ? m.get(parte) : null;
            }
            return actual;
        }

        public long largo(String ruta) {
            return ((Number) get(ruta)).longValue();
        }

        public String texto(String ruta) {
            Object v = get(ruta);
            return v == null ? null : v.toString();
        }
    }

    public ClienteApi conToken(String token) {
        this.token = token;
        return this;
    }

    public ClienteApi conStepUp(String stepUp) {
        this.stepUp = stepUp;
        return this;
    }

    public ClienteApi conCabecera(String nombre, String valor) {
        if (valor == null) {
            cabeceras.remove(nombre);
        } else {
            cabeceras.put(nombre, valor);
        }
        return this;
    }

    public String token() {
        return token;
    }

    public Respuesta get(String ruta) {
        return llamar(HttpMethod.GET, ruta, null);
    }

    public Respuesta post(String ruta, Object cuerpo) {
        return llamar(HttpMethod.POST, ruta, cuerpo);
    }

    public Respuesta patch(String ruta, Object cuerpo) {
        return llamar(HttpMethod.PATCH, ruta, cuerpo);
    }

    public Respuesta put(String ruta, Object cuerpo) {
        return llamar(HttpMethod.PUT, ruta, cuerpo);
    }

    public Respuesta delete(String ruta) {
        return llamar(HttpMethod.DELETE, ruta, null);
    }

    private Respuesta llamar(HttpMethod metodo, String ruta, Object cuerpo) {
        var req = http.method(metodo).uri(ruta).headers(h -> {
            if (token != null) {
                h.setBearerAuth(token);
            }
            if (stepUp != null) {
                h.set("X-Step-Up", stepUp);
            }
            cabeceras.forEach(h::set);
        });
        if (cuerpo != null) {
            req = req.contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(cuerpo));
        }
        return req.exchange((rq, rs) -> {
            byte[] bytes = rs.getBody().readAllBytes();
            String texto = new String(bytes, StandardCharsets.UTF_8);
            Object valor = texto.isBlank() ? null
                    : texto.trim().startsWith("{") || texto.trim().startsWith("[") ? json.readValue(texto, Object.class) : texto;
            return new Respuesta(rs.getStatusCode().value(), valor);
        });
    }
}
