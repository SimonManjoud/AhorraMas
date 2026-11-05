package ve.impulsatech.ahorramas.pagos.adaptador.entrada.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.compartido.seguridad.NivelStepUp;
import ve.impulsatech.ahorramas.compartido.seguridad.UsuarioActual;
import ve.impulsatech.ahorramas.compartido.seguridad.VerificadorStepUp;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.entrada.CasosDeUsoPagos.*;

/** Adaptador REST del pago de TDC (contrato §4). */
@RestController
@RequestMapping("/api/v1")
class PagosController {

    private final GestionarTarjetas tarjetas;
    private final PagarTarjeta pagos;
    private final VerificadorStepUp stepUp;

    PagosController(GestionarTarjetas tarjetas, PagarTarjeta pagos, VerificadorStepUp stepUp) {
        this.tarjetas = tarjetas;
        this.pagos = pagos;
        this.stepUp = stepUp;
    }

    record PagoDto(@NotNull Long idTarjeta, @NotNull Long idCuentaOrigen, @NotNull @Positive Long montoCts, boolean beneficiarioConfirmado) {
    }

    @GetMapping("/tarjetas")
    List<VistaTarjeta> tarjetas() {
        return tarjetas.tarjetas(UsuarioActual.id());
    }

    @PostMapping("/tarjetas/{id}/beneficiario")
    BeneficiarioConfirmable beneficiario(@PathVariable long id) {
        return tarjetas.confirmarBeneficiario(UsuarioActual.id(), id);
    }

    @GetMapping("/pagos-tdc/cuentas-origen")
    List<CuentaOrigen> cuentasOrigen() {
        return pagos.cuentasOrigen(UsuarioActual.id());
    }

    @GetMapping("/pagos-tdc/cotizacion")
    Cotizacion cotizacion(@RequestParam long montoCts) {
        return pagos.cotizar(montoCts);
    }

    /** Idempotency-Key obligatoria (R-15) y step-up PIN + OTP (R-16). */
    @PostMapping("/pagos-tdc")
    ResponseEntity<VistaPago> pagar(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody PagoDto d) {
        stepUp.exigir(NivelStepUp.PIN_OTP);
        ResultadoPago r = pagos.pagar(UsuarioActual.id(), idempotencyKey,
                new ComandoPago(d.idTarjeta(), d.idCuentaOrigen(), d.montoCts(), d.beneficiarioConfirmado()));
        return ResponseEntity.status(r.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(r.pago());
    }

    @GetMapping("/pagos-tdc")
    List<VistaPago> listar() {
        return pagos.listar(UsuarioActual.id());
    }

    @GetMapping("/pagos-tdc/{id}")
    VistaPago obtener(@PathVariable long id) {
        return pagos.obtener(UsuarioActual.id(), id);
    }

    @GetMapping("/limites")
    UsoLimites limites() {
        return pagos.limites(UsuarioActual.id());
    }
}
