package ve.impulsatech.ahorramas.compartido.seguridad;

/**
 * Puerto que el módulo de identidad implementa para que la seguridad transversal pueda anular
 * sesiones de dispositivos revocados (F3 · paso 6: cambio de SIM, root, robo o 3 fallas).
 */
public interface ValidadorSesion {

    boolean dispositivoActivo(long idUsuario, long idDispositivo);

    boolean saldoRapidoActivo(long idUsuario, long idDispositivo);
}
