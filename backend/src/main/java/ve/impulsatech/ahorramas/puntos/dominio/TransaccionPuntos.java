package ve.impulsatech.ahorramas.puntos.dominio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/**
 * TRANSACCION_PUNTOS con sus ASIENTO_PUNTOS (partida doble, R-21): al menos dos asientos y
 * Σ débitos = Σ créditos. Inmutable: los errores se corrigen con una transacción de reverso (R-10).
 */
public record TransaccionPuntos(OffsetDateTime fechaHora, String concepto, String descripcion, String claveOrigen, List<Asiento> asientos) {

    public static final Set<String> CONCEPTOS = Set.of("meta", "pago_puntual", "racha", "referido", "canje", "reverso");

    public record Asiento(long idCuentaPuntos, String naturaleza, long puntos) {
    }

    public TransaccionPuntos {
        if (!CONCEPTOS.contains(concepto)) {
            throw ErrorNegocio.validacion("Concepto de puntos inválido: " + concepto);
        }
        if (asientos == null || asientos.size() < 2) {
            throw ErrorNegocio.regla("R-21", "Una transacción de puntos requiere al menos dos asientos");
        }
        long debitos = 0;
        long creditos = 0;
        for (Asiento a : asientos) {
            if (a.puntos() <= 0) {
                throw ErrorNegocio.regla("R-21", "Cada asiento debe tener puntos positivos");
            }
            switch (a.naturaleza()) {
                case "debito" -> debitos += a.puntos();
                case "credito" -> creditos += a.puntos();
                default -> throw ErrorNegocio.validacion("Naturaleza inválida");
            }
        }
        if (debitos != creditos) {
            throw ErrorNegocio.regla("R-21", "La transacción de puntos está descuadrada");
        }
        asientos = List.copyOf(asientos);
    }

    /** Mueve puntos de una cuenta a otra: débito en el origen, crédito en el destino. */
    public static TransaccionPuntos transferencia(long origen, long destino, long puntos, String concepto, String descripcion,
                                                 String claveOrigen, OffsetDateTime ahora) {
        return new TransaccionPuntos(ahora, concepto, descripcion, claveOrigen,
                List.of(new Asiento(origen, "debito", puntos), new Asiento(destino, "credito", puntos)));
    }
}
