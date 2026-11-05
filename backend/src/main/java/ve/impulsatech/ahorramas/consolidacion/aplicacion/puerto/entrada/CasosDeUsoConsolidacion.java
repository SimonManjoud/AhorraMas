package ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.entrada;

import java.time.OffsetDateTime;
import java.util.List;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.MovimientoGuardado;
import ve.impulsatech.ahorramas.consolidacion.dominio.Institucion;
import ve.impulsatech.ahorramas.consolidacion.dominio.PosicionGlobal;

/** Casos de uso de consolidación. */
public final class CasosDeUsoConsolidacion {

    private CasosDeUsoConsolidacion() {
    }

    /** F1: vinculación multibanco por consentimiento revocable. */
    public interface GestionarConsentimientos {
        List<Institucion> instituciones();

        List<SolicitudCreada> solicitar(long idUsuario, List<Long> instituciones, boolean permiteDebitoTdc);

        ResultadoCallback completar(String estado, String codigo, String error);

        List<VistaConsentimiento> listar(long idUsuario);

        void revocar(long idUsuario, long idConsentimiento, String motivo);
    }

    public interface SincronizarCuentas {
        void sincronizarUsuario(long idUsuario);

        int sincronizarTodos();
    }

    public interface ConsultarConsolidado {
        PosicionGlobal posicion(long idUsuario);

        void cambiarAlias(long idUsuario, long idCuenta, String alias);

        List<MovimientoGuardado> movimientos(long idUsuario, long idCuenta);
    }

    public record SolicitudCreada(long idConsentimiento, long idInstitucion, String institucion, String estatus, String urlAutorizacion) {
    }

    public record ResultadoCallback(boolean exito, String institucion, String mensaje) {
    }

    public record VistaConsentimiento(long idConsentimiento, long idInstitucion, String institucion, String estatus, boolean permiteLectura,
                                      boolean permiteDebitoTdc, OffsetDateTime fechaOtorgamiento, OffsetDateTime fechaVencimiento) {
    }
}
