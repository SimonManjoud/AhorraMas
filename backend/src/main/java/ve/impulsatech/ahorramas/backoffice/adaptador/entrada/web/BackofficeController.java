package ve.impulsatech.ahorramas.backoffice.adaptador.entrada.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.backoffice.aplicacion.ServicioBackoffice;
import ve.impulsatech.ahorramas.backoffice.aplicacion.puerto.salida.RepositorioBackoffice.*;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;
import ve.impulsatech.ahorramas.pagos.api.PagosApi;

/** API de operaciones del backoffice (contrato §9). Requiere token de operador; cada acción valida el rol. */
@RestController
@RequestMapping("/api/v1/backoffice")
class BackofficeController {

    private final ServicioBackoffice backoffice;
    private final PagosApi pagos;
    private final UsuariosApi usuarios;
    private final Auditoria auditoria;
    private final Reloj reloj;

    BackofficeController(ServicioBackoffice backoffice, PagosApi pagos, UsuariosApi usuarios, Auditoria auditoria, Reloj reloj) {
        this.backoffice = backoffice;
        this.pagos = pagos;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    record LoginDto(@NotBlank String correoCorporativo, @NotBlank String clave) {
    }

    record FechaDto(LocalDate fecha) {
    }

    record CasoDto(@NotBlank String estatus, String resolucion) {
    }

    record AlertaDto(@NotBlank String estatus, String justificacion) {
    }

    record RosDto(@NotNull Long idAlerta) {
    }

    record LimiteDto(@NotNull @Positive Long montoMaxDiarioCts, @NotNull @Positive Integer maxOperacionesDiarias, @NotBlank String motivoCambio) {
    }

    record AuditoriaDto(boolean cadenaIntegra, List<Auditoria.EventoAuditoria> eventos) {
    }

    @PostMapping("/auth/login")
    ServicioBackoffice.SesionOperador login(@Valid @RequestBody LoginDto d) {
        return backoffice.login(d.correoCorporativo(), d.clave());
    }

    @GetMapping("/pagos")
    List<PagosApi.ResumenPago> pagos(@RequestParam(required = false) String estado) {
        return pagos.pagosPorEstado(estado, 200);
    }

    @PostMapping("/conciliaciones")
    PagosApi.ResumenConciliacion conciliar(@RequestBody(required = false) FechaDto d) {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "conciliacion");
        return pagos.conciliar(d == null || d.fecha() == null ? reloj.hoy().minusDays(1) : d.fecha());
    }

    @GetMapping("/conciliaciones")
    PagosApi.ResumenConciliacion conciliacion(@RequestParam(required = false) LocalDate fecha) {
        return pagos.conciliacion(fecha == null ? reloj.hoy().minusDays(1) : fecha);
    }

    @GetMapping("/casos")
    List<Caso> casos(@RequestParam(required = false) String estatus) {
        return backoffice.casos(estatus);
    }

    @PatchMapping("/casos/{id}")
    Caso caso(@PathVariable long id, @Valid @RequestBody CasoDto d) {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "soporte", "conciliacion");
        return backoffice.actualizarCaso(id, d.estatus(), d.resolucion(), UsuarioActual.idOperador());
    }

    @GetMapping("/alertas-aml")
    List<Alerta> alertas(@RequestParam(required = false) String estatus) {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "oficial_cumpl");
        return backoffice.alertas(estatus);
    }

    @PatchMapping("/alertas-aml/{id}")
    Alerta alerta(@PathVariable long id, @Valid @RequestBody AlertaDto d) {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "oficial_cumpl");
        return backoffice.revisarAlerta(id, d.estatus(), d.justificacion(), UsuarioActual.idOperador());
    }

    @PostMapping("/reportes-regulatorios")
    Reporte ros(@Valid @RequestBody RosDto d) {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "oficial_cumpl");
        return backoffice.crearRos(d.idAlerta(), UsuarioActual.idOperador());
    }

    @PostMapping("/reportes-regulatorios/{id}/enviar")
    Reporte enviar(@PathVariable long id) {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "oficial_cumpl");
        return backoffice.enviarReporte(id, UsuarioActual.idOperador());
    }

    @GetMapping("/reportes-regulatorios")
    List<Reporte> reportes() {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "oficial_cumpl");
        return backoffice.reportes();
    }

    @GetMapping("/usuarios/{id}/limite")
    UsuariosApi.LimiteVigente limite(@PathVariable long id) {
        return usuarios.limiteVigente(id);
    }

    @PutMapping("/usuarios/{id}/limite")
    UsuariosApi.LimiteVigente cambiarLimite(@PathVariable long id, @Valid @RequestBody LimiteDto d) {
        ServicioBackoffice.exigirRol(UsuarioActual.rolOperador(), "oficial_cumpl");
        return usuarios.cambiarLimite(id, d.montoMaxDiarioCts(), d.maxOperacionesDiarias(), d.motivoCambio(), UsuarioActual.idOperador());
    }

    @GetMapping("/auditoria")
    AuditoriaDto auditoria(@RequestParam(defaultValue = "100") int limite) {
        return new AuditoriaDto(auditoria.cadenaIntegra(), auditoria.ultimos(limite));
    }

    @GetMapping("/yo")
    Map<String, Object> yo() {
        return Map.of("idOperador", UsuarioActual.idOperador(), "rol", UsuarioActual.rolOperador());
    }
}
