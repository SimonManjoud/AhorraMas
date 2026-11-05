package ve.impulsatech.ahorramas.cofre.adaptador.entrada.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.cofre.api.CofreApi.ReglaCreada;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.*;
import ve.impulsatech.ahorramas.compartido.seguridad.NivelStepUp;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.compartido.seguridad.VerificadorStepUp;

/** Adaptador REST del Cofre Protector (contrato §3). */
@RestController
@RequestMapping("/api/v1")
class CofreController {

    private final GestionarMetas metas;
    private final ConsultarRutaDelDia ruta;
    private final MoverCofre movimientos;
    private final GestionarReglas reglas;
    private final ConsultarRacha racha;
    private final GestionarSello sello;
    private final ConsultarEspejo espejo;
    private final VerificadorStepUp stepUp;

    CofreController(GestionarMetas metas, ConsultarRutaDelDia ruta, MoverCofre movimientos, GestionarReglas reglas, ConsultarRacha racha,
                    GestionarSello sello, ConsultarEspejo espejo, VerificadorStepUp stepUp) {
        this.metas = metas;
        this.ruta = ruta;
        this.movimientos = movimientos;
        this.reglas = reglas;
        this.racha = racha;
        this.sello = sello;
        this.espejo = espejo;
        this.stepUp = stepUp;
    }

    record MetaDto(@NotBlank String nombre, @NotBlank String categoria, @NotNull @Positive Long montoObjetivoUsdCts,
                   @NotNull LocalDate fechaObjetivo, String frecuenciaCuota) {
    }

    record EstatusDto(@NotBlank String estatus) {
    }

    record AporteDto(@NotNull @Positive Long montoUsdCts, String origen, Long idCuentaOrigen) {
    }

    record RetiroDto(@NotNull @Positive Long montoUsdCts) {
    }

    record ReglaDto(@NotBlank String tipo, Integer porcentajePb, Long multiploRedondeoCts, Long montoFijoUsdCts) {
    }

    record SelloDto(@NotNull @Positive Long montoSelladoUsdCts) {
    }

    // --- metas
    @PostMapping("/metas")
    @ResponseStatus(HttpStatus.CREATED)
    VistaMeta crear(@Valid @RequestBody MetaDto d) {
        return metas.crear(UsuarioActual.id(), new NuevaMeta(d.nombre(), d.categoria(), d.montoObjetivoUsdCts(), d.fechaObjetivo(), d.frecuenciaCuota()));
    }

    @GetMapping("/metas")
    List<VistaMeta> listar() {
        return metas.listar(UsuarioActual.id());
    }

    @GetMapping("/metas/{id}")
    VistaMeta obtener(@PathVariable long id) {
        return metas.obtener(UsuarioActual.id(), id);
    }

    @PatchMapping("/metas/{id}")
    VistaMeta estatus(@PathVariable long id, @Valid @RequestBody EstatusDto d) {
        return metas.cambiarEstatus(UsuarioActual.id(), id, d.estatus());
    }

    // --- Ruta del día
    @GetMapping("/metas/{id}/ruta-del-dia")
    VistaRuta rutaDelDia(@PathVariable long id) {
        return ruta.rutaDelDia(UsuarioActual.id(), id);
    }

    @GetMapping("/ruta-del-dia")
    List<VistaRuta> rutasDelDia() {
        return ruta.rutasDelDia(UsuarioActual.id());
    }

    // --- ledger del cofre
    @PostMapping("/metas/{id}/aportes")
    @ResponseStatus(HttpStatus.CREATED)
    ResultadoAporte aportar(@PathVariable long id, @Valid @RequestBody AporteDto d) {
        return movimientos.aportar(UsuarioActual.id(), id, d.montoUsdCts(), d.origen(), d.idCuentaOrigen());
    }

    @PostMapping("/metas/{id}/retiros")
    ResultadoRetiro retirar(@PathVariable long id, @Valid @RequestBody RetiroDto d) {
        stepUp.exigir(NivelStepUp.PIN);
        return movimientos.retirar(UsuarioActual.id(), id, d.montoUsdCts());
    }

    @GetMapping("/metas/{id}/aportes")
    List<VistaAporte> aportes(@PathVariable long id) {
        return movimientos.aportes(UsuarioActual.id(), id);
    }

    // --- reglas
    @PostMapping("/metas/{id}/reglas")
    @ResponseStatus(HttpStatus.CREATED)
    ReglaCreada crearRegla(@PathVariable long id, @Valid @RequestBody ReglaDto d) {
        stepUp.exigir(NivelStepUp.PIN);
        return reglas.crear(UsuarioActual.id(), id, d.tipo(), d.porcentajePb(), d.multiploRedondeoCts(), d.montoFijoUsdCts());
    }

    @GetMapping("/metas/{id}/reglas")
    List<ReglaCreada> listarReglas(@PathVariable long id) {
        return reglas.listar(UsuarioActual.id(), id);
    }

    @PatchMapping("/reglas/{id}")
    ReglaCreada estatusRegla(@PathVariable long id, @Valid @RequestBody EstatusDto d) {
        if ("activa".equals(d.estatus())) {
            stepUp.exigir(NivelStepUp.PIN);   // reactivar una regla es volver a aprobarla
        }
        return reglas.cambiarEstatus(UsuarioActual.id(), id, d.estatus());
    }

    // --- racha
    @GetMapping("/racha")
    VistaRacha racha() {
        return racha.racha(UsuarioActual.id());
    }

    // --- sello
    @PostMapping("/metas/{id}/sello")
    @ResponseStatus(HttpStatus.CREATED)
    VistaSello sellar(@PathVariable long id, @Valid @RequestBody SelloDto d) {
        stepUp.exigir(NivelStepUp.PIN);
        return sello.sellar(UsuarioActual.id(), id, d.montoSelladoUsdCts());
    }

    @PostMapping("/metas/{id}/sello/romper")
    VistaSello romperSello(@PathVariable long id) {
        stepUp.exigir(NivelStepUp.PIN);
        return sello.romper(UsuarioActual.id(), id);
    }

    // --- Espejo de Impacto
    @GetMapping("/espejo-impacto")
    VistaEspejo espejo(@RequestParam(required = false) Integer anio, @RequestParam(required = false) Integer mes) {
        return espejo.espejo(UsuarioActual.id(), anio, mes);
    }
}
