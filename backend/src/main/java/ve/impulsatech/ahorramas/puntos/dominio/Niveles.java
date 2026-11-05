package ve.impulsatech.ahorramas.puntos.dominio;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** NIVEL_FIDELIDAD (Base, Gold, Platinum) y regla de canje (R-22). */
public final class Niveles {

    private Niveles() {
    }

    public record Nivel(int idNivel, String nombre, long puntosMinimos, int orden) {
    }

    public record Recompensa(long idRecompensa, String descripcion, long costoPuntos, String comercio, long idComercio, Nivel nivelMinimo,
                             LocalDate fechaInicio, LocalDate fechaFin, Integer stockDisponible) {
        public boolean vigente(LocalDate hoy) {
            return !hoy.isBefore(fechaInicio) && (fechaFin == null || !hoy.isAfter(fechaFin));
        }
    }

    /** El nivel se gana con los puntos acumulados históricamente (los canjes no te bajan de nivel). */
    public static Nivel nivelPara(long puntosGanados, List<Nivel> niveles) {
        return niveles.stream().filter(n -> n.puntosMinimos() <= puntosGanados).max(Comparator.comparingInt(Nivel::orden))
                .orElseThrow(() -> new IllegalStateException("No hay niveles configurados"));
    }

    public static Optional<Nivel> siguiente(Nivel actual, List<Nivel> niveles) {
        return niveles.stream().filter(n -> n.orden() > actual.orden()).min(Comparator.comparingInt(Nivel::orden));
    }

    /** R-22: saldo suficiente, nivel suficiente, recompensa vigente y con stock. */
    public static void validarCanje(Recompensa r, long saldo, Nivel nivelUsuario, LocalDate hoy) {
        if (!r.vigente(hoy)) {
            throw ErrorNegocio.regla("R-22", "Esta recompensa ya no está vigente");
        }
        if (r.stockDisponible() != null && r.stockDisponible() <= 0) {
            throw ErrorNegocio.regla("R-22", "Esta recompensa está agotada");
        }
        if (nivelUsuario.orden() < r.nivelMinimo().orden()) {
            throw ErrorNegocio.regla("R-22", "Necesitas nivel " + r.nivelMinimo().nombre() + " para esta recompensa");
        }
        if (saldo < r.costoPuntos()) {
            throw ErrorNegocio.regla("R-22", "No tienes puntos suficientes (te faltan " + (r.costoPuntos() - saldo) + ")");
        }
    }
}
