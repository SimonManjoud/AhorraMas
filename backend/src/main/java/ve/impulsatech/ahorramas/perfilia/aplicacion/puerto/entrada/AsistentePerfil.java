package ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.entrada;

import java.time.OffsetDateTime;
import java.util.List;
import ve.impulsatech.ahorramas.cofre.api.CofreApi.ReglaCreada;

/** Caso de uso F4: recomendación explicable que no ejecuta nada sin aprobación. */
public interface AsistentePerfil {

    String DISCLAIMER = "Recomendación orientativa generada con reglas y un modelo entrenado con datos sintéticos; "
            + "no constituye asesoría financiera. Ahorra+ nunca mueve tu dinero sin tu aprobación.";

    ResultadoEvaluacion evaluar(long idUsuario, String objetivoPrincipal, int horizonteMeses, String toleranciaRiesgo);

    ResultadoDecision decidir(long idUsuario, long idRecomendacion, boolean aceptar, Boolean entendioExplicacion, Long idMeta);

    List<VistaRecomendacion> recomendaciones(long idUsuario);

    record DistribucionVista(String instrumento, int porcentajePb, String nivelRiesgo) {
    }

    record ReglaSugeridaVista(String tipo, Integer porcentajePb) {
    }

    record VistaRecomendacion(long idRecomendacion, String estatus, String explicacion, List<DistribucionVista> distribucion,
                              ReglaSugeridaVista reglaSugerida, String versionModelo, String disclaimer, String perfilResultante,
                              OffsetDateTime fechaHora) {
    }

    record ResultadoEvaluacion(long idEvaluacion, String perfilResultante, VistaRecomendacion recomendacion) {
    }

    record ResultadoDecision(String estatus, ReglaCreada reglaCreada) {
    }
}
