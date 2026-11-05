package ve.impulsatech.ahorramas.simuladores.suiche;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.ArchivoLiquidacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Autorizacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Beneficiario;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Captura;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.ConsultaBeneficiario;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Operacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.RespuestaSuiche;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Reverso;

/** Suiche interbancario simulado (contrato §3). */
@RestController
public class SuicheController {

    private final SuicheService suiche;

    public SuicheController(SuicheService suiche) {
        this.suiche = suiche;
    }

    @PostMapping("/suiche/beneficiarios/consulta")
    public Beneficiario beneficiario(@RequestBody ConsultaBeneficiario cuerpo) {
        if (cuerpo.bancoEmisor() == null || cuerpo.refTarjeta() == null) {
            throw ApiException.solicitudInvalida("Se requieren 'bancoEmisor' y 'refTarjeta'");
        }
        return suiche.consultarBeneficiario(cuerpo.bancoEmisor(), cuerpo.refTarjeta());
    }

    @PostMapping("/suiche/autorizaciones")
    public RespuestaSuiche autorizar(@RequestBody Autorizacion cuerpo) {
        return suiche.autorizar(cuerpo);
    }

    @PostMapping("/suiche/capturas")
    public RespuestaSuiche capturar(@RequestBody Captura cuerpo) {
        return suiche.capturar(cuerpo.referencia(), cuerpo.codigoAutorizacion());
    }

    @PostMapping("/suiche/reversos")
    public RespuestaSuiche reversar(@RequestBody Reverso cuerpo) {
        return suiche.reversar(cuerpo.referencia(), cuerpo.motivo());
    }

    @GetMapping("/suiche/operaciones/{referencia}")
    public Operacion operacion(@PathVariable String referencia) {
        return suiche.consultar(referencia);
    }

    @GetMapping("/suiche/liquidaciones/{fecha}")
    public ArchivoLiquidacion liquidacion(@PathVariable String fecha) {
        try {
            return suiche.liquidacion(LocalDate.parse(fecha));
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FECHA_INVALIDA", "La fecha debe ser yyyy-MM-dd");
        }
    }
}
