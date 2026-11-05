package ve.impulsatech.ahorramas.identidad.api;

/** Consultas que otros módulos pueden hacer a Identidad. */
public interface UsuariosApi {

    /** Lanza {@code KYC_PENDIENTE} si el usuario no tiene una verificación aprobada. */
    void exigirKycAprobado(long idUsuario);

    ContactoUsuario contacto(long idUsuario);

    /** Documento de identidad (para el login_hint del consentimiento en el banco). */
    String documento(long idUsuario);

    /** Identificador seudónimo: lo único que se comparte con la IA (R-23). */
    long seudonimo(long idUsuario);

    LimiteVigente limiteVigente(long idUsuario);

    /** F3: el token de saldo rápido solo sirve desde el dispositivo al que se ligó. */
    void validarHuellaDispositivo(long idUsuario, long idDispositivo, String huellaCliente);

    /** Cambio de límite desde backoffice (R-05: cierra el vigente y abre uno nuevo). */
    LimiteVigente cambiarLimite(long idUsuario, long montoMaxDiarioCts, int maxOperacionesDiarias, String motivo, long idOperador);

    record ContactoUsuario(long idUsuario, String nombre, String correo, String telefono) {
    }

    record LimiteVigente(long montoMaxDiarioCts, int maxOperacionesDiarias) {
    }
}
