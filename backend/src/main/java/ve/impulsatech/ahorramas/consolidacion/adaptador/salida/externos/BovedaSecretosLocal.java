package ve.impulsatech.ahorramas.consolidacion.adaptador.salida.externos;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.compartido.cifrado.ServicioCifrado;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.BovedaSecretos;

/**
 * Bóveda de secretos simulada: esquema {@code boveda} aislado, contenido cifrado con una llave
 * distinta a la de PII. En producción este adaptador se reemplaza por uno de HashiCorp Vault + KMS.
 */
@Component
class BovedaSecretosLocal implements BovedaSecretos {

    private final JdbcClient jdbc;
    private final ServicioCifrado cifrado;
    private final Reloj reloj;

    BovedaSecretosLocal(JdbcClient jdbc, ServicioCifrado cifrado, Reloj reloj) {
        this.jdbc = jdbc;
        this.cifrado = cifrado;
        this.reloj = reloj;
    }

    @Override
    public String guardar(String secreto) {
        String ref = "vault:consentimientos/" + UUID.randomUUID();
        jdbc.sql("INSERT INTO boveda.secreto (ref_secreto, contenido_cifrado, fecha_creacion) VALUES (:r, :c, :f)")
                .param("r", ref).param("c", cifrado.cifrarSecreto(secreto)).param("f", reloj.ahora()).update();
        return ref;
    }

    @Override
    public String leer(String referencia) {
        return jdbc.sql("SELECT contenido_cifrado FROM boveda.secreto WHERE ref_secreto = :r AND NOT destruido")
                .param("r", referencia).query(String.class).optional()
                .map(cifrado::descifrarSecreto)
                .orElseThrow(() -> ErrorNegocio.regla("R-02", "El token del consentimiento ya no existe en la bóveda"));
    }

    @Override
    public void destruir(String referencia) {
        jdbc.sql("UPDATE boveda.secreto SET destruido = TRUE, contenido_cifrado = '' WHERE ref_secreto = :r").param("r", referencia).update();
    }
}
