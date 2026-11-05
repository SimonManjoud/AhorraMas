package ve.impulsatech.ahorramas.backoffice.dominio;

import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de monitoreo AML (versión MVP; umbrales en USD a la tasa BCV del día). Generan alertas
 * para revisión humana del oficial de cumplimiento; nunca bloquean por sí solas.
 */
public final class ReglasAml {

    public static final long UMBRAL_MEDIO_USD_CTS = 100_000;     // $1.000 en un pago
    public static final long UMBRAL_ALTO_USD_CTS = 500_000;      // $5.000 en un pago
    public static final long UMBRAL_DIARIO_USD_CTS = 300_000;    // $3.000 acumulados en el día
    public static final int OPERACIONES_FRACCIONAMIENTO = 4;     // muchos pagos pequeños el mismo día

    private ReglasAml() {
    }

    public record Alerta(String regla, String nivelRiesgo, String descripcion) {
    }

    public static List<Alerta> evaluarPago(long montoUsdCts, long acumuladoDiaUsdCts, int operacionesDia) {
        List<Alerta> alertas = new ArrayList<>();
        if (montoUsdCts >= UMBRAL_ALTO_USD_CTS) {
            alertas.add(new Alerta("AML-01-MONTO", "alto", "Pago individual igual o superior a $5.000"));
        } else if (montoUsdCts >= UMBRAL_MEDIO_USD_CTS) {
            alertas.add(new Alerta("AML-01-MONTO", "medio", "Pago individual igual o superior a $1.000"));
        }
        if (acumuladoDiaUsdCts >= UMBRAL_DIARIO_USD_CTS) {
            alertas.add(new Alerta("AML-02-ACUMULADO", "medio", "Pagos acumulados en el día iguales o superiores a $3.000"));
        }
        if (operacionesDia >= OPERACIONES_FRACCIONAMIENTO) {
            alertas.add(new Alerta("AML-03-FRACCIONAMIENTO", "bajo", "Posible fraccionamiento: " + operacionesDia + " pagos en el día"));
        }
        return alertas;
    }
}
