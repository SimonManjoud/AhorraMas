package ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.salida;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil.Clasificacion;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil.Rasgos;

/** Puertos de salida del asistente de IA. */
public final class PuertosPerfil {

    private PuertosPerfil() {
    }

    /** Proveedor LLM: solo redacta. Recibe id seudónimo + rasgos; nunca PII. */
    public interface RedactorExplicaciones {
        Optional<String> redactar(long idSeudonimo, Clasificacion clasificacion, Rasgos rasgos);
    }

    public interface Repositorio {
        VersionModelo versionActiva();

        long guardarEvaluacion(long idUsuario, long idVersion, Rasgos rasgos, String perfil, OffsetDateTime ahora);

        long guardarRecomendacion(long idEvaluacion, String explicacion, Map<String, Integer> distribucionPb, String reglaTipo, long reglaValor,
                                  OffsetDateTime ahora);

        Optional<Recomendacion> recomendacion(long idRecomendacion);

        List<Recomendacion> recomendaciones(long idUsuario);

        void decidir(long idRecomendacion, String estatus, Boolean entendio, OffsetDateTime ahora);
    }

    public record VersionModelo(long idVersion, String versionReglas, String versionModelo) {
        public String etiqueta() {
            return versionReglas + " / " + versionModelo;
        }
    }

    public record Distribucion(String instrumento, int porcentajePb, String nivelRiesgo) {
    }

    public record Recomendacion(long idRecomendacion, long idUsuario, String perfil, OffsetDateTime fechaHora, String explicacion, String estatus,
                                List<Distribucion> distribucion, String reglaTipo, Long reglaValor, String version) {
    }
}
