package ve.impulsatech.ahorramas.puntos.api;

/** Eventos de dominio de Puntos. */
public final class EventosPuntos {

    private EventosPuntos() {
    }

    public record PuntosAcreditados(long idUsuario, long puntos, String concepto, String descripcion) {
    }

    public record NivelAlcanzado(long idUsuario, String nivel) {
    }
}
