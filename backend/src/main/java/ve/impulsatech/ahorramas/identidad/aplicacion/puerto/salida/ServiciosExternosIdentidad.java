package ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida;

import java.time.LocalDate;
import java.util.List;

/** Puertos hacia sistemas externos e infraestructura usados por Identidad. */
public final class ServiciosExternosIdentidad {

    private ServiciosExternosIdentidad() {
    }

    /** Proveedor KYC (cédula, prueba de vida, listas de sanciones). */
    public interface ProveedorKyc {
        ResultadoKyc verificar(SolicitudKyc solicitud);
    }

    /** Almacén de objetos cifrado para fotos de cédula y selfie (C4: Documentos KYC). */
    public interface AlmacenObjetos {
        /** Guarda el contenido cifrado y devuelve la referencia opaca. */
        String guardarCifrado(byte[] contenido, String prefijo);
    }

    /** Canal SMS para OTP. */
    public interface EnvioSms {
        void enviar(String telefono, String titulo, String mensaje);
    }

    /** Hash lento y con sal para el PIN (nunca se guarda en claro). */
    public interface HasherPin {
        String hash(String pin);

        boolean coincide(String pin, String hash);
    }

    public record SolicitudKyc(String nacionalidad, int cedula, String primerNombre, String primerApellido,
                               LocalDate fechaNacimiento, List<DocumentoHash> documentos) {
    }

    public record DocumentoHash(String tipo, String hashSha256) {
    }

    public record ResultadoKyc(String refProveedor, String resultado, Boolean pruebaVidaSuperada,
                               Boolean coincideListaSanciones, String motivo) {
    }
}
