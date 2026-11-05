package ve.impulsatech.ahorramas.simuladores.llm;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Proveedor de IA (LLM) simulado (contrato §5). */
@RestController
public class LlmController {

    private final LlmService llm;

    public LlmController(LlmService llm) {
        this.llm = llm;
    }

    /** Se recibe el cuerpo crudo para poder aplicar la guardia de PII sobre el texto completo. */
    @PostMapping(value = "/llm/v1/explicaciones", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Explicacion explicar(@RequestBody String cuerpo) {
        return llm.explicar(cuerpo);
    }

    public record Explicacion(String texto, String modelo) {
    }
}
