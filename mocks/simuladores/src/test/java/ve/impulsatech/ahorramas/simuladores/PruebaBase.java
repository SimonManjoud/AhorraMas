package ve.impulsatech.ahorramas.simuladores;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

/** Base común: contexto completo con un retardo de timeout corto (300 ms) y MockMvc. */
@SpringBootTest(properties = "simulador.suiche.retardo-timeout-ms=300")
public abstract class PruebaBase {

    @Autowired
    protected WebApplicationContext contexto;

    protected MockMvc mvc;

    @BeforeEach
    void prepararMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    protected ResultActions postJson(String url, String cuerpo) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    protected static String cuerpo(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected static <T> T leer(MvcResult r, String ruta) throws Exception {
        return JsonPath.read(cuerpo(r), ruta);
    }
}
