package ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi.LimiteVigente;
import ve.impulsatech.ahorramas.identidad.dominio.DesafioOtp;
import ve.impulsatech.ahorramas.identidad.dominio.Dispositivo;
import ve.impulsatech.ahorramas.identidad.dominio.Usuario;
import ve.impulsatech.ahorramas.identidad.dominio.VerificacionKyc;

/** Puerto de persistencia del módulo de identidad. */
public interface RepositorioIdentidad {

    Usuario guardarUsuario(Usuario usuario);

    Optional<Usuario> usuarioPorDocumento(String nacionalidadDoc, int cedula);

    Optional<Usuario> usuarioPorId(long idUsuario);

    boolean existeCorreo(String correoNormalizado);

    boolean existeTelefono(String telefonoNormalizado);

    boolean existeSeudonimo(long seudonimo);

    void guardarCredencial(long idUsuario, String pinHash, OffsetDateTime ahora);

    Optional<CredencialPin> credencial(long idUsuario);

    void actualizarIntentosFallidos(long idUsuario, int intentos, OffsetDateTime ahora);

    Dispositivo guardarDispositivo(Dispositivo dispositivo);

    Optional<Dispositivo> dispositivoPorHuella(long idUsuario, String huellaHash);

    Optional<Dispositivo> dispositivo(long idDispositivo);

    int contarDispositivos(long idUsuario);

    VerificacionKyc guardarVerificacion(VerificacionKyc verificacion);

    boolean tieneKycAprobado(long idUsuario);

    void guardarDesafio(DesafioOtp desafio);

    Optional<DesafioOtp> desafio(UUID idDesafio);

    Optional<LimiteVigente> limiteVigente(long idUsuario);

    void cerrarLimiteVigente(long idUsuario, OffsetDateTime fechaFin);

    void crearLimite(long idUsuario, long montoMaxDiarioCts, int maxOperacionesDiarias, OffsetDateTime inicio, String motivo);

    record CredencialPin(long idUsuario, String pinHash, int intentosFallidos) {
    }
}
