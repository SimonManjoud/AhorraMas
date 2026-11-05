package ve.impulsatech.ahorramas.identidad.dominio;

import java.time.OffsetDateTime;
import java.util.List;

/** VERIFICACION_KYC + sus DOCUMENTO_KYC (cédula y selfie, guardados cifrados fuera de la BD). */
public record VerificacionKyc(
        Long idVerificacion,
        long idUsuario,
        OffsetDateTime fechaSolicitud,
        OffsetDateTime fechaResultado,
        String resultado,
        Boolean pruebaVidaSuperada,
        Boolean coincideListaSanciones,
        String refProveedorKyc,
        String motivoRechazo,
        List<Documento> documentos) {

    public static final List<String> TIPOS_REQUERIDOS = List.of("cedula", "selfie");

    public record Documento(int secuencia, String tipo, String refAlmacenObjetos, String hashSha256, OffsetDateTime fechaCarga) {
    }

    public boolean aprobada() {
        return "aprobado".equals(resultado);
    }
}
