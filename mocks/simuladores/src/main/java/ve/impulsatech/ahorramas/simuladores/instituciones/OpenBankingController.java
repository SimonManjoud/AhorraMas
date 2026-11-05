package ve.impulsatech.ahorramas.simuladores.instituciones;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.instituciones.OAuthService.TokenEmitido;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Cuenta;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.InyectarMovimiento;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Movimiento;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Saldo;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Tarjeta;

/** Catálogo y datos de Open Banking protegidos con Bearer (contrato §2 y §2.2). */
@RestController
public class OpenBankingController {

    private final OAuthService oauth;
    private final BancoService banco;

    public OpenBankingController(OAuthService oauth, BancoService banco) {
        this.oauth = oauth;
        this.banco = banco;
    }

    @GetMapping("/ob/instituciones")
    public List<Institucion> instituciones() {
        return Institucion.CATALOGO;
    }

    @GetMapping("/ob/{codigo}/cuentas")
    public List<Cuenta> cuentas(@PathVariable String codigo,
                                @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        TokenEmitido t = token(codigo, auth);
        return banco.cuentas(codigo, t.cedula());
    }

    @GetMapping("/ob/{codigo}/cuentas/{refCuenta}/saldo")
    public Saldo saldo(@PathVariable String codigo, @PathVariable String refCuenta,
                       @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        TokenEmitido t = token(codigo, auth);
        return banco.saldo(codigo, t.cedula(), refCuenta);
    }

    @GetMapping("/ob/{codigo}/cuentas/{refCuenta}/movimientos")
    public List<Movimiento> movimientos(@PathVariable String codigo, @PathVariable String refCuenta,
                                        @RequestParam(required = false) String desde,
                                        @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        TokenEmitido t = token(codigo, auth);
        return banco.movimientos(codigo, t.cedula(), refCuenta, fecha(desde));
    }

    @GetMapping("/ob/{codigo}/tarjetas")
    public List<Tarjeta> tarjetas(@PathVariable String codigo,
                                  @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        TokenEmitido t = token(codigo, auth);
        return banco.tarjetas(codigo, t.cedula());
    }

    /** Control demo: inyecta un movimiento (y ajusta el saldo) para disparar reglas de ahorro. */
    @PostMapping("/simulador/ob/{codigo}/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    public Movimiento inyectar(@PathVariable String codigo, @RequestBody InyectarMovimiento cuerpo) {
        return banco.inyectar(codigo, cuerpo);
    }

    private TokenEmitido token(String codigo, String auth) {
        return oauth.validar(Institucion.obtener(codigo), auth);
    }

    private static LocalDate fecha(String desde) {
        if (desde == null || desde.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(desde);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FECHA_INVALIDA", "'desde' debe ser yyyy-MM-dd");
        }
    }
}
