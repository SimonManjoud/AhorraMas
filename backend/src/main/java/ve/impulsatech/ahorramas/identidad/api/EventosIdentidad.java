package ve.impulsatech.ahorramas.identidad.api;

import java.time.OffsetDateTime;

/** Eventos de dominio publicados por Identidad (se persisten en el registro de eventos = outbox). */
public final class EventosIdentidad {

    private EventosIdentidad() {
    }

    public record UsuarioRegistrado(long idUsuario, OffsetDateTime fechaHora) {
    }

    public record KycResuelto(long idUsuario, boolean aprobado, String motivo) {
    }

    public record DispositivoRegistrado(long idUsuario, long idDispositivo, String modelo, boolean primerDispositivo) {
    }

    public record DispositivoRevocado(long idUsuario, long idDispositivo, String motivo) {
    }

    /** Se envió un OTP (el código nunca viaja en el evento). */
    public record OtpEnviado(long idUsuario, String proposito) {
    }
}
