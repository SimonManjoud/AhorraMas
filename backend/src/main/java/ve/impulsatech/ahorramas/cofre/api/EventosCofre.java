package ve.impulsatech.ahorramas.cofre.api;

import java.time.LocalDate;

/** Eventos de dominio del Cofre Protector. */
public final class EventosCofre {

    private EventosCofre() {
    }

    /** Aporte al cofre: Puntos acredita base + bono de racha (financiados por la cuenta sistema). */
    public record AporteRegistrado(long idUsuario, long idMeta, long idAporte, String nombreMeta, long montoUsdCts, String origen,
                                   long puntosBase, long bonoRacha, int multiplicadorX100) {
    }

    public record MetaCumplida(long idUsuario, long idMeta, String nombreMeta, long montoObjetivoUsdCts) {
    }

    public record SelloRoto(long idUsuario, long idMeta, String nombreMeta, int multiplicadorPerdidoX100) {
    }

    public record ColchonUsado(long idUsuario, LocalDate fecha, int colchonesRestantes, int diasRacha) {
    }

    public record RachaCerrada(long idUsuario, long idRacha, int dias, String motivo) {
    }
}
