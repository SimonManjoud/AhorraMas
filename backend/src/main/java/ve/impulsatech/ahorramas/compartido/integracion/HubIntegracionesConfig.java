package ve.impulsatech.ahorramas.compartido.integracion;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Hub de integraciones (capa anticorrupción): clientes HTTP hacia los sistemas externos, todos
 * simulados en el MVP ({@code mocks/simuladores}). Timeouts explícitos y huella de certificado
 * cliente que simula mTLS. Cada módulo implementa su adaptador sobre estos clientes.
 */
@Configuration(proxyBeanMethods = false)
public class HubIntegracionesConfig {

    public static final String CABECERA_MTLS = "X-Client-Cert-Fingerprint";

    @Bean
    @Qualifier("simuladores")
    RestClient clienteSimuladores(RestClient.Builder builder, PropiedadesIntegraciones p) {
        return crear(builder, p, Duration.ofMillis(p.timeoutLecturaMs()));
    }

    /** Cliente del suiche con su propio timeout (30 s en producción; corto en demo). */
    @Bean
    @Qualifier("suiche")
    RestClient clienteSuiche(RestClient.Builder builder, PropiedadesIntegraciones p) {
        return crear(builder, p, Duration.ofMillis(p.timeoutSuicheMs()));
    }

    private static RestClient crear(RestClient.Builder builder, PropiedadesIntegraciones p, Duration lectura) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(p.timeoutConexionMs()))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(lectura);
        return builder.clone()
                .baseUrl(p.urlSimuladores())
                .requestFactory(fabrica)
                .defaultHeader(CABECERA_MTLS, p.huellaCertificadoMtls())
                .build();
    }
}
