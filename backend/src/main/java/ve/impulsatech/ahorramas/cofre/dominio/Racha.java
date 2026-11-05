package ve.impulsatech.ahorramas.cofre.dominio;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Engranaje 2 · Racha con días colchón (R-11). Cada aporte (aunque sea el redondeo) suma un día.
 * Un día sin aporte NO rompe la racha si quedan días colchón en el mes: los ingresos en Venezuela
 * son irregulares y la app no castiga a quien tiene un mes malo.
 */
public record Racha(Long idRacha, long idUsuario, LocalDate fechaInicio, LocalDate fechaFin, String motivoCierre, List<Dia> dias) {

    public record Dia(LocalDate fecha, String tipo) {
        public boolean colchon() {
            return "colchon".equals(tipo);
        }
    }

    /** MULTIPLICADOR_RACHA: tramo de puntos y días colchón por mes calendario. */
    public record Tramo(int diasMinimos, int multiplicadorX100, int diasColchon) {
    }

    public enum Decision { DIA_CUBIERTO, USAR_COLCHON, CERRAR }

    public boolean activa() {
        return fechaFin == null;
    }

    public int diasConsecutivos() {
        return dias.size();
    }

    public int diasAporte() {
        return (int) dias.stream().filter(d -> !d.colchon()).count();
    }

    public int colchonesUsados() {
        return (int) dias.stream().filter(Dia::colchon).count();
    }

    public boolean tieneDia(LocalDate fecha) {
        return dias.stream().anyMatch(d -> d.fecha().equals(fecha));
    }

    public Optional<LocalDate> ultimoDia() {
        return dias.stream().map(Dia::fecha).max(Comparator.naturalOrder());
    }

    public static Tramo tramoPara(int diasConsecutivos, List<Tramo> tramos) {
        return tramos.stream().filter(t -> t.diasMinimos() <= diasConsecutivos)
                .max(Comparator.comparingInt(Tramo::diasMinimos))
                .orElse(new Tramo(0, 100, 0));
    }

    public static Optional<Tramo> siguienteTramo(int diasConsecutivos, List<Tramo> tramos) {
        return tramos.stream().filter(t -> t.diasMinimos() > diasConsecutivos).min(Comparator.comparingInt(Tramo::diasMinimos));
    }

    public Tramo tramo(List<Tramo> tramos) {
        return tramoPara(diasConsecutivos(), tramos);
    }

    /** Colchones que quedan en el mes calendario de {@code fecha} según el tramo actual. */
    public int colchonesDisponibles(LocalDate fecha, List<Tramo> tramos) {
        YearMonth mes = YearMonth.from(fecha);
        long usadosMes = dias.stream().filter(d -> d.colchon() && YearMonth.from(d.fecha()).equals(mes)).count();
        return (int) Math.max(0, tramo(tramos).diasColchon() - usadosMes);
    }

    /** Cierre de un día: ¿hubo aporte?, ¿se usa un colchón?, ¿o se rompe la racha? */
    public Decision evaluarDia(LocalDate fecha, List<Tramo> tramos) {
        if (tieneDia(fecha)) {
            return Decision.DIA_CUBIERTO;
        }
        return colchonesDisponibles(fecha, tramos) > 0 ? Decision.USAR_COLCHON : Decision.CERRAR;
    }
}
