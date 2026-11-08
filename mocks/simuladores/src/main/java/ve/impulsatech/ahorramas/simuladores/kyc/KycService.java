package ve.impulsatech.ahorramas.simuladores.kyc;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.comun.RegistroTitulares;
import ve.impulsatech.ahorramas.simuladores.kyc.KycController.ResultadoKyc;
import ve.impulsatech.ahorramas.simuladores.kyc.KycController.SolicitudKyc;

/**
 * Verificación de identidad simulada:
 * cédula terminada en 666 ⇒ coincide con lista de sanciones; terminada en 000 ⇒ falla la prueba de vida.
 */
@Service
public class KycService {

    private final RegistroTitulares titulares;

    public KycService(RegistroTitulares titulares) {
        this.titulares = titulares;
    }

    public ResultadoKyc verificar(SolicitudKyc s) {
        if (s == null || s.cedula() == null || s.cedula() <= 0) {
            throw ApiException.solicitudInvalida("Se requiere 'cedula' numérica");
        }
        Set<String> tipos = s.documentos() == null ? Set.of() : s.documentos().stream()
                .filter(d -> d != null && d.tipo() != null && d.hashSha256() != null && !d.hashSha256().isBlank())
                .map(d -> d.tipo().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (!tipos.contains("cedula") || !tipos.contains("selfie")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCUMENTOS_INCOMPLETOS",
                    "Se requieren los documentos 'cedula' y 'selfie' (con su hashSha256)");
        }
        String ref = "KYC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        long terminacion = s.cedula() % 1000;
        if (terminacion == 666) {
            return new ResultadoKyc(ref, "rechazado", true, true, "Coincidencia en lista de sanciones");
        }
        if (terminacion == 0) {
            return new ResultadoKyc(ref, "rechazado", false, false, "Prueba de vida no superada");
        }
        titulares.registrar(RegistroTitulares.cedula(s.nacionalidad(), s.cedula()), s.primerNombre(), s.primerApellido());
        return new ResultadoKyc(ref, "aprobado", true, false, null);
    }
}
