package ve.impulsatech.ahorramas.consolidacion.adaptador.entrada.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.entrada.CasosDeUsoConsolidacion.*;
import ve.impulsatech.ahorramas.consolidacion.dominio.PosicionGlobal;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;

/** Adaptador REST de consolidación (contrato §2) y saldo rápido (§1.6). */
@RestController
@RequestMapping("/api/v1")
class ConsolidacionController {

    private final GestionarConsentimientos consentimientos;
    private final SincronizarCuentas sincronizacion;
    private final ConsultarConsolidado consolidado;
    private final CuentasApi cuentas;
    private final TasasApi tasas;
    private final UsuariosApi usuarios;

    ConsolidacionController(GestionarConsentimientos consentimientos, SincronizarCuentas sincronizacion, ConsultarConsolidado consolidado,
                            CuentasApi cuentas, TasasApi tasas, UsuariosApi usuarios) {
        this.consentimientos = consentimientos;
        this.sincronizacion = sincronizacion;
        this.consolidado = consolidado;
        this.cuentas = cuentas;
        this.tasas = tasas;
        this.usuarios = usuarios;
    }

    record InstitucionDto(long idInstitucion, String codigo, String nombre, String tipo, String estatusIntegracion) {
    }

    record SolicitudDto(@NotEmpty List<Long> instituciones, boolean permiteDebitoTdc) {
    }

    record TasaDto(LocalDate fecha, long valorBsX10000, String fuente) {
    }

    record CuentaDto(long idCuenta, String alias, String ultimos4, String tipo, String moneda, long saldoCts, long saldoBsCts,
                     long saldoUsdCts, boolean soloLectura) {
    }

    record InstitucionConsolidadaDto(long idInstitucion, String nombre, String tipo, long totalBsCts, long totalUsdCts, List<CuentaDto> cuentas) {
    }

    record ConsolidadoDto(TasaDto tasa, long totalBsCts, long totalUsdCts, OffsetDateTime actualizadoEn, List<InstitucionConsolidadaDto> instituciones) {
    }

    record AliasDto(String alias) {
    }

    record MovimientoDto(long idMovimiento, OffsetDateTime fechaHora, String naturaleza, String clasificacion, long montoCts,
                         String moneda, String descripcion) {
    }

    record BancoSaldoDto(String nombre, String tipo, long totalBsCts, long totalUsdCts) {
    }

    record SaldoRapidoDto(long totalBsCts, long totalUsdCts, OffsetDateTime actualizadoEn, List<BancoSaldoDto> bancos) {
    }

    @GetMapping("/instituciones")
    List<InstitucionDto> instituciones() {
        return consentimientos.instituciones().stream()
                .map(i -> new InstitucionDto(i.idInstitucion(), i.codigo(), i.nombre(), i.tipo(), i.estatusIntegracion())).toList();
    }

    @PostMapping("/consentimientos")
    @ResponseStatus(HttpStatus.CREATED)
    List<SolicitudCreada> solicitar(@Valid @RequestBody SolicitudDto d) {
        return consentimientos.solicitar(UsuarioActual.id(), d.instituciones(), d.permiteDebitoTdc());
    }

    @GetMapping("/consentimientos")
    List<VistaConsentimiento> listar() {
        return consentimientos.listar(UsuarioActual.id());
    }

    @DeleteMapping("/consentimientos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revocar(@PathVariable long id, @RequestParam(required = false) String motivo) {
        consentimientos.revocar(UsuarioActual.id(), id, motivo);
    }

    /** Redirect del banco (público). Responde HTML para el navegador del teléfono. */
    @GetMapping("/consentimientos/callback")
    ResponseEntity<String> callback(@RequestParam(required = false) String state, @RequestParam(required = false) String code,
                    @RequestParam(required = false) String error) {
        ResultadoCallback r;
        try {
            r = consentimientos.completar(state, code, error);
        } catch (ErrorNegocio e) {
            r = new ResultadoCallback(false, null, e.getMessage());
        }
        String color = r.exito() ? "#0f766e" : "#b45309";
        String html = """
                <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
                <title>Ahorra+</title></head>
                <body style="font-family:system-ui,sans-serif;background:#f4f7f6;display:flex;min-height:100vh;align-items:center;justify-content:center;margin:0">
                <div style="background:#fff;border-radius:16px;padding:32px;max-width:360px;text-align:center;box-shadow:0 4px 18px rgba(0,0,0,.08)">
                <div style="font-size:22px;font-weight:700;color:%s">Ahorra+</div>
                <p style="font-size:17px;color:#1f2937">%s</p>
                <p style="font-size:13px;color:#6b7280">Ahorra+ nunca conoció tus claves bancarias. Puedes revocar este acceso cuando quieras.</p>
                </div></body></html>
                """.formatted(color, HtmlUtils.htmlEscape(r.mensaje()));
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(html);
    }

    @PostMapping("/consolidado/sincronizar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void sincronizar() {
        sincronizacion.sincronizarUsuario(UsuarioActual.id());
    }

    @GetMapping("/consolidado")
    ConsolidadoDto consolidado() {
        PosicionGlobal p = consolidado.posicion(UsuarioActual.id());
        return new ConsolidadoDto(tasa(p.tasa()), p.totalBsCts(), p.totalUsdCts(), p.actualizadoEn(),
                p.instituciones().stream().map(i -> new InstitucionConsolidadaDto(i.idInstitucion(), i.nombre(), i.tipo(), i.totalBsCts(),
                        i.totalUsdCts(), i.cuentas().stream().map(l -> new CuentaDto(l.cuenta().idCuenta(), l.cuenta().alias(),
                                l.cuenta().ultimos4(), l.cuenta().tipo(), l.cuenta().moneda().name(), l.cuenta().saldoOCero(), l.saldoBsCts(),
                                l.saldoUsdCts(), l.cuenta().soloLectura())).toList())).toList());
    }

    @PatchMapping("/cuentas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void alias(@PathVariable long id, @RequestBody AliasDto d) {
        consolidado.cambiarAlias(UsuarioActual.id(), id, d.alias());
    }

    @GetMapping("/cuentas/{id}/movimientos")
    List<MovimientoDto> movimientos(@PathVariable long id) {
        return consolidado.movimientos(UsuarioActual.id(), id).stream()
                .map(m -> new MovimientoDto(m.idMovimiento(), m.fechaHora(), m.naturaleza(), m.clasificacion(), m.montoCts(), m.moneda(),
                        m.descripcion())).toList();
    }

    @GetMapping("/tasas/hoy")
    TasaDto tasaHoy() {
        return tasa(tasas.tasaHoy());
    }

    /**
     * F3: total enmascarable y su desglose por institución (sin cuentas ni movimientos). El token es de solo lectura
     * y está ligado al dispositivo de confianza: solo ese equipo ve los montos antes de ingresar el PIN.
     */
    @GetMapping("/saldo-rapido")
    SaldoRapidoDto saldoRapido(@RequestHeader(value = "X-Dispositivo", required = false) String huella) {
        long id = UsuarioActual.id();
        usuarios.validarHuellaDispositivo(id, UsuarioActual.idDispositivo(), huella);
        var t = cuentas.totales(id);
        var bancos = consolidado.posicion(id).instituciones().stream()
                .map(i -> new BancoSaldoDto(i.nombre(), i.tipo(), i.totalBsCts(), i.totalUsdCts())).toList();
        return new SaldoRapidoDto(t.totalBsCts(), t.totalUsdCts(), t.actualizadoEn(), bancos);
    }

    private static TasaDto tasa(TasaBcv t) {
        return new TasaDto(t.fecha(), t.valorBsX10000(), "BCV");
    }
}
