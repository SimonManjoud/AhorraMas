package ve.impulsatech.ahorramas.compartido.integracion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ahorra.integraciones")
public record PropiedadesIntegraciones(
        String urlSimuladores,
        /** URL de los simuladores tal como la ve el navegador del teléfono (p. ej. http://10.0.2.2:8090). */
        String urlPublicaSimuladores,
        /** URL del núcleo tal como la ve el navegador del teléfono (redirect_uri del OAuth). */
        String urlPublicaNucleo,
        String clientId,
        String huellaCertificadoMtls,
        int timeoutConexionMs,
        int timeoutLecturaMs,
        int timeoutSuicheMs) {
}
