package ve.impulsatech.ahorramas.simuladores.kyc;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Proveedor KYC simulado (contrato §4). */
@RestController
public class KycController {

    private final KycService kyc;

    public KycController(KycService kyc) {
        this.kyc = kyc;
    }

    @PostMapping("/kyc/verificaciones")
    public ResultadoKyc verificar(@RequestBody SolicitudKyc solicitud) {
        return kyc.verificar(solicitud);
    }

    public record Documento(String tipo, String hashSha256) {
    }

    public record SolicitudKyc(String nacionalidad, Long cedula, String primerNombre, String primerApellido,
                               String fechaNacimiento, List<Documento> documentos) {
    }

    public record ResultadoKyc(String refProveedor, String resultado, boolean pruebaVidaSuperada,
                               boolean coincideListaSanciones, String motivo) {
    }
}
