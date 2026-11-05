package ve.impulsatech.ahorramas.perfilia.adaptador.salida.externos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.salida.PuertosPerfil.RedactorExplicaciones;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil.Clasificacion;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil.Rasgos;

/**
 * Adaptador del proveedor LLM: arma el payload SOLO con datos seudonimizados (id seudónimo, perfil,
 * rangos y la distribución). Si el proveedor falla, el núcleo usa la explicación de respaldo.
 */
@Component
class RedactorLlmHttp implements RedactorExplicaciones {

    private static final Logger log = LoggerFactory.getLogger(RedactorLlmHttp.class);

    private final RestClient http;

    RedactorLlmHttp(@Qualifier("simuladores") RestClient http) {
        this.http = http;
    }

    record Respuesta(String texto, String modelo) {
    }

    @Override
    public Optional<String> redactar(long idSeudonimo, Clasificacion c, Rasgos r) {
        Map<String, Object> rasgos = new LinkedHashMap<>();
        rasgos.put("rangoIngreso", r.rangoIngreso());
        rasgos.put("volatilidadSaldoPb", r.volatilidadSaldoPb());
        rasgos.put("objetivoPrincipal", r.objetivoPrincipal());
        rasgos.put("horizonteMeses", r.horizonteMeses());
        rasgos.put("toleranciaRiesgo", r.toleranciaRiesgo());
        rasgos.put("metasActivas", r.metasActivas());
        List<Map<String, Object>> dist = c.distribucionPb().entrySet().stream()
                .map(e -> Map.<String, Object>of("instrumento", e.getKey(), "porcentajePb", e.getValue())).toList();
        try {
            Respuesta resp = http.post().uri("/llm/v1/explicaciones")
                    .body(Map.of("idSeudonimo", idSeudonimo, "perfil", c.perfil(), "rasgos", rasgos, "distribucion", dist))
                    .retrieve().body(Respuesta.class);
            return Optional.ofNullable(resp).map(Respuesta::texto).filter(t -> !t.isBlank());
        } catch (RestClientException e) {
            log.warn("Proveedor LLM no disponible, se usa la explicación de respaldo: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
