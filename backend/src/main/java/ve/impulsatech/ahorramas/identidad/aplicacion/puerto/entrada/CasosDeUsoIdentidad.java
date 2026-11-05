package ve.impulsatech.ahorramas.identidad.aplicacion.puerto.entrada;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import ve.impulsatech.ahorramas.compartido.seguridad.ServicioTokens.TokenEmitido;

/** Puertos de entrada (casos de uso) del módulo de identidad. */
public final class CasosDeUsoIdentidad {

    private CasosDeUsoIdentidad() {
    }

    /** Registro, login con PIN y autenticación reforzada (step-up). */
    public interface Acceso {
        Sesion registrar(ComandoRegistro comando);

        Sesion iniciarSesion(ComandoLogin comando);

        DesafioEmitido solicitarOtp(long idUsuario, String proposito);

        StepUpEmitido stepUp(long idUsuario, long idDispositivo, String pin, UUID idDesafio, String otp);

        PerfilUsuario perfil(long idUsuario);
    }

    /** Dispositivos y saldo rápido (F3). */
    public interface Dispositivos {
        TokenEmitido activarSaldoRapido(long idUsuario, long idDispositivo);

        void desactivarSaldoRapido(long idUsuario, long idDispositivo);

        void revocar(long idUsuario, long idDispositivo, String motivo);
    }

    /** Verificación de identidad (KYC). */
    public interface VerificarIdentidad {
        ResultadoVerificacion verificar(long idUsuario, List<DocumentoSubido> documentos);
    }

    public record DatosDispositivo(String huella, String plataforma, String modelo) {
    }

    public record ComandoRegistro(String nacionalidadDoc, Integer cedula, String primerNombre, String segundoNombre,
                                  String primerApellido, String segundoApellido, LocalDate fechaNacimiento, String correo,
                                  String telefonoMovil, String pin, DatosDispositivo dispositivo) {
    }

    public record ComandoLogin(String nacionalidadDoc, Integer cedula, String pin, DatosDispositivo dispositivo) {
    }

    public record Sesion(String accessToken, OffsetDateTime expiraEn, ResumenUsuario usuario, long idDispositivo) {
    }

    public record ResumenUsuario(long idUsuario, String primerNombre, String primerApellido, boolean kycAprobado, String estatus) {
    }

    public record PerfilUsuario(long idUsuario, String nacionalidadDoc, int cedula, String primerNombre, String primerApellido,
                                String correo, String telefonoMovil, boolean kycAprobado, String estatus, OffsetDateTime fechaRegistro) {
    }

    public record DesafioEmitido(UUID idDesafio, OffsetDateTime expiraEn, String otpDemo) {
    }

    public record StepUpEmitido(String stepUpToken, String nivel, OffsetDateTime expiraEn) {
    }

    public record DocumentoSubido(String tipo, byte[] contenido) {
    }

    public record ResultadoVerificacion(long idVerificacion, String resultado, Boolean pruebaVidaSuperada,
                                        Boolean coincideListaSanciones, String motivoRechazo) {
    }
}
