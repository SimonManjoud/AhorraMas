package ve.impulsatech.ahorramas.identidad.adaptador.salida.externos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ve.impulsatech.ahorramas.compartido.cifrado.ServicioCifrado;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.ServiciosExternosIdentidad.*;

/** Adaptadores de salida de Identidad hacia el proveedor KYC, SMS, almacén de objetos y hash de PIN. */
public final class AdaptadoresExternosIdentidad {

    private AdaptadoresExternosIdentidad() {
    }

    /** Proveedor KYC vía HTTP (simulado en mocks/simuladores: POST /kyc/verificaciones). */
    @Component
    static class ProveedorKycHttp implements ProveedorKyc {
        private final RestClient http;

        ProveedorKycHttp(@Qualifier("simuladores") RestClient http) {
            this.http = http;
        }

        @Override
        public ResultadoKyc verificar(SolicitudKyc s) {
            try {
                Map<String, Object> cuerpo = Map.of("nacionalidad", s.nacionalidad(), "cedula", s.cedula(),
                        "primerNombre", s.primerNombre().toUpperCase(), "primerApellido", s.primerApellido().toUpperCase(),
                        "fechaNacimiento", s.fechaNacimiento().toString(),
                        "documentos", s.documentos().stream().map(d -> Map.of("tipo", d.tipo(), "hashSha256", d.hashSha256())).toList());
                RespuestaKyc r = http.post().uri("/kyc/verificaciones").body(cuerpo).retrieve().body(RespuestaKyc.class);
                if (r == null || r.resultado() == null) {
                    throw ErrorNegocio.servicioExterno("El proveedor KYC no respondió");
                }
                return new ResultadoKyc(r.refProveedor(), r.resultado(), r.pruebaVidaSuperada(), r.coincideListaSanciones(), r.motivo());
            } catch (RestClientException e) {
                throw ErrorNegocio.servicioExterno("No pudimos contactar al proveedor de verificación de identidad");
            }
        }

        record RespuestaKyc(String refProveedor, String resultado, Boolean pruebaVidaSuperada, Boolean coincideListaSanciones, String motivo) {
        }
    }

    /** Envío de SMS (OTP) vía el simulador de notificaciones. */
    @Component
    static class EnvioSmsHttp implements EnvioSms {
        private static final Logger log = LoggerFactory.getLogger(EnvioSmsHttp.class);
        private final RestClient http;

        EnvioSmsHttp(@Qualifier("simuladores") RestClient http) {
            this.http = http;
        }

        @Override
        public void enviar(String telefono, String titulo, String mensaje) {
            try {
                http.post().uri("/notificaciones/sms")
                        .body(Map.of("destino", telefono, "titulo", titulo, "mensaje", mensaje, "tipo", "otp"))
                        .retrieve().toBodilessEntity();
            } catch (RestClientException e) {
                // El OTP queda registrado; en el MVP no se bloquea el flujo si el canal SMS no está disponible.
                log.warn("No se pudo enviar el SMS: {}", e.getMessage());
            }
        }
    }

    /** Almacén de objetos cifrado en disco (en producción: S3 con cifrado del lado del servidor + KMS). */
    @Component
    static class AlmacenObjetosArchivo implements AlmacenObjetos {
        private final Path raiz;
        private final ServicioCifrado cifrado;

        AlmacenObjetosArchivo(@Value("${ahorra.almacen-objetos.directorio}") String directorio, ServicioCifrado cifrado) {
            this.raiz = Path.of(directorio).toAbsolutePath();
            this.cifrado = cifrado;
        }

        @Override
        public String guardarCifrado(byte[] contenido, String prefijo) {
            try {
                Files.createDirectories(raiz);
                String ref = prefijo + "-" + UUID.randomUUID() + ".enc";
                Files.write(raiz.resolve(ref), cifrado.cifrarBytes(contenido));
                return "objetos://kyc/" + ref;
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo guardar el documento cifrado", e);
            }
        }
    }

    @Component
    static class HasherPinBcrypt implements HasherPin {
        private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

        @Override
        public String hash(String pin) {
            return encoder.encode(pin);
        }

        @Override
        public boolean coincide(String pin, String hash) {
            return encoder.matches(pin, hash);
        }
    }
}
