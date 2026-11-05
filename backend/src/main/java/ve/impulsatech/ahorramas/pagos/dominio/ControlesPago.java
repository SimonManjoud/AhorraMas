package ve.impulsatech.ahorramas.pagos.dominio;

import java.util.Map;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;

/** Controles previos a la autorización (F2 · paso 3): límite diario y reglas antifraude de velocidad. */
public final class ControlesPago {

    /** Antifraude: ventana corta en la que se cuentan los intentos de pago. */
    public static final int MINUTOS_VENTANA = 10;

    private ControlesPago() {
    }

    public static void validarLimite(long montoMaxDiarioCts, int maxOperaciones, long usadoHoyCts, int operacionesHoy, long montoCts) {
        if (operacionesHoy >= maxOperaciones) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.LIMITE_EXCEDIDO, "LIMITE_EXCEDIDO",
                    "Alcanzaste el máximo de " + maxOperaciones + " pagos por día", Map.of("operacionesHoy", operacionesHoy));
        }
        if (usadoHoyCts + montoCts > montoMaxDiarioCts) {
            long disponible = Math.max(0, montoMaxDiarioCts - usadoHoyCts);
            throw new ErrorNegocio(ErrorNegocio.Tipo.LIMITE_EXCEDIDO, "LIMITE_EXCEDIDO",
                    "El pago supera tu límite diario. Disponible hoy: " + Montos.formatear(disponible, Moneda.VES),
                    Map.of("disponibleCts", disponible));
        }
    }

    public static void validarVelocidad(int intentosRecientes, int maxIntentosVentana) {
        if (intentosRecientes >= maxIntentosVentana) {
            throw ErrorNegocio.regla("ANTIFRAUDE",
                    "Por tu seguridad, espera unos minutos antes de hacer otro pago (demasiados intentos seguidos)");
        }
    }
}
