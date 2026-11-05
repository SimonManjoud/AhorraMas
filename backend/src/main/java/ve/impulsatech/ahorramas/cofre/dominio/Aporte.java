package ve.impulsatech.ahorramas.cofre.dominio;

import java.time.OffsetDateTime;
import java.util.Collection;

/**
 * APORTE: asiento del ledger inmutable del cofre (R-10). Un retiro es un asiento de tipo
 * {@code retiro}; nunca se edita ni se borra un asiento.
 */
public record Aporte(
        Long idAporte,
        long idMeta,
        Long idCuenta,
        Long idRegla,
        Long idCuota,
        OffsetDateTime fechaHora,
        String tipo,
        String origen,
        long montoUsdCts,
        long montoBsCts,
        long tasaValorBsX10000,
        String claveOrigen) {

    public boolean esRetiro() {
        return "retiro".equals(tipo);
    }

    /** Monto con signo: + aporte, − retiro. */
    public long montoConSigno() {
        return esRetiro() ? -montoUsdCts : montoUsdCts;
    }

    /** R-08: acumulado = Σ aportes − Σ retiros (se calcula, nunca se edita). */
    public static long acumulado(Collection<Aporte> asientos) {
        return asientos.stream().mapToLong(Aporte::montoConSigno).sum();
    }
}
