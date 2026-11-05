package ve.impulsatech.ahorramas.perfilia.adaptador.entrada.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.compartido.seguridad.NivelStepUp;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.compartido.seguridad.VerificadorStepUp;
import ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.entrada.AsistentePerfil;

/** Adaptador REST del asistente de perfil de riesgo (contrato §6). */
@RestController
@RequestMapping("/api/v1")
class AsistenteController {

    private final AsistentePerfil asistente;
    private final VerificadorStepUp stepUp;

    AsistenteController(AsistentePerfil asistente, VerificadorStepUp stepUp) {
        this.asistente = asistente;
        this.stepUp = stepUp;
    }

    record EvaluacionDto(@NotBlank String objetivoPrincipal, @NotNull Integer horizonteMeses, @NotBlank String toleranciaRiesgo) {
    }

    record DecisionDto(boolean aceptar, Boolean entendioExplicacion, Long idMeta) {
    }

    @PostMapping("/perfil/evaluaciones")
    AsistentePerfil.ResultadoEvaluacion evaluar(@Valid @RequestBody EvaluacionDto d) {
        return asistente.evaluar(UsuarioActual.id(), d.objetivoPrincipal(), d.horizonteMeses(), d.toleranciaRiesgo());
    }

    @PostMapping("/recomendaciones/{id}/decision")
    AsistentePerfil.ResultadoDecision decidir(@PathVariable long id, @RequestBody DecisionDto d) {
        stepUp.exigir(NivelStepUp.PIN);
        return asistente.decidir(UsuarioActual.id(), id, d.aceptar(), d.entendioExplicacion(), d.idMeta());
    }

    @GetMapping("/recomendaciones")
    List<AsistentePerfil.VistaRecomendacion> recomendaciones() {
        return asistente.recomendaciones(UsuarioActual.id());
    }
}
