package ve.impulsatech.ahorramas.notificaciones.adaptador.entrada.eventos;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.ColchonUsado;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.MetaCumplida;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.RachaCerrada;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.SelloRoto;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.ConsentimientoActivado;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.ConsentimientoRevocado;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.MovimientoRegistrado;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.DispositivoRegistrado;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.DispositivoRevocado;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.KycResuelto;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.OtpEnviado;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.ServicioNotificaciones;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.PagoEstadoCambiado;
import ve.impulsatech.ahorramas.puntos.api.EventosPuntos.NivelAlcanzado;

/** Convierte eventos de dominio en avisos al usuario ("alertas en cada movimiento"). */
@Component
class OyentesNotificaciones {

    private final ServicioNotificaciones n;

    OyentesNotificaciones(ServicioNotificaciones n) {
        this.n = n;
    }

    @ApplicationModuleListener
    void pago(PagoEstadoCambiado e) {
        String monto = Montos.formatear(e.montoCts(), Moneda.VES);
        switch (e.estadoNuevo()) {
            case "CAPTURADO" -> n.push(e.idUsuario(), "pago", "Pago aplicado", "Tu pago de " + monto + " a la TDC ***" + e.ultimos4Tarjeta() + " fue aplicado.");
            case "RECHAZADO" -> n.push(e.idUsuario(), "pago", "Pago rechazado", "Tu pago de " + monto + " fue rechazado: " + e.motivo()
                    + ". No se debitó nada ni se cobró comisión.");
            case "EN_VERIFICACION" -> n.push(e.idUsuario(), "pago", "Pago en proceso", "Estamos confirmando tu pago de " + monto
                    + " con el suiche. No lo repitas.");
            case "REVERSADO" -> n.push(e.idUsuario(), "pago", "Pago reversado", "Tu pago de " + monto + " fue reversado; el dinero vuelve a tu cuenta.");
            case "CON_DIFERENCIA" -> n.push(e.idUsuario(), "pago", "Revisando tu pago", "Detectamos una diferencia en tu pago de " + monto
                    + ". Abrimos un caso y te avisaremos.");
            default -> { }
        }
    }

    /** Alerta de movimiento: solo para movimientos recientes (no para la historia de la primera sincronización). */
    @ApplicationModuleListener
    void movimiento(MovimientoRegistrado e) {
        if (e.fechaHora().isAfter(n.ahora().minusDays(1))) {
            String signo = "credito".equals(e.naturaleza()) ? "Ingreso" : "Débito";
            n.push(e.idUsuario(), "movimiento", signo + " en tu cuenta",
                    signo + " de " + Montos.formatear(e.montoCts(), Moneda.de(e.moneda())) + (e.descripcion() == null ? "" : ": " + e.descripcion()));
        }
    }

    @ApplicationModuleListener
    void metaCumplida(MetaCumplida e) {
        n.push(e.idUsuario(), "meta", "¡Meta cumplida!", "Llegaste a " + Montos.formatear(e.montoObjetivoUsdCts(), Moneda.USD) + " en «"
                + e.nombreMeta() + "». Ganaste 500 puntos extra.");
    }

    @ApplicationModuleListener
    void selloRoto(SelloRoto e) {
        n.push(e.idUsuario(), "meta", "Sello roto", "Rompiste el sello de «" + e.nombreMeta()
                + "». Perdiste el multiplicador de racha; tu dinero sigue intacto en tu banco.");
    }

    @ApplicationModuleListener
    void colchon(ColchonUsado e) {
        n.push(e.idUsuario(), "racha", "Usamos un día colchón", "Ayer no aportaste, pero tu racha de " + e.diasRacha()
                + " días sigue viva. Te quedan " + e.colchonesRestantes() + " días colchón este mes.");
    }

    @ApplicationModuleListener
    void rachaCerrada(RachaCerrada e) {
        String motivo = "sello_roto".equals(e.motivo()) ? "porque rompiste un sello" : "porque no quedaban días colchón";
        n.push(e.idUsuario(), "racha", "Tu racha terminó", "Tu racha de " + e.dias() + " días terminó " + motivo
                + ". Con tu próximo aporte empiezas una nueva.");
    }

    @ApplicationModuleListener
    void nivel(NivelAlcanzado e) {
        n.push(e.idUsuario(), "meta", "¡Subiste a " + e.nivel() + "!", "Tus buenos hábitos te llevaron al nivel " + e.nivel() + ".");
    }

    @ApplicationModuleListener
    void otp(OtpEnviado e) {
        n.registrarSms(e.idUsuario(), "otp", "Código de verificación", "Te enviamos un código OTP por SMS (" + e.proposito() + ").");
    }

    @ApplicationModuleListener
    void dispositivo(DispositivoRegistrado e) {
        if (!e.primerDispositivo()) {
            n.push(e.idUsuario(), "seguridad", "Nuevo dispositivo", "Se inició sesión desde un nuevo dispositivo (" + e.modelo()
                    + "). Si no fuiste tú, revócalo desde Ajustes.");
        }
    }

    @ApplicationModuleListener
    void dispositivoRevocado(DispositivoRevocado e) {
        n.push(e.idUsuario(), "seguridad", "Dispositivo revocado", "Cerramos la sesión de un dispositivo (motivo: " + e.motivo()
                + ") y anulamos su saldo rápido.");
    }

    @ApplicationModuleListener
    void kyc(KycResuelto e) {
        n.push(e.idUsuario(), "seguridad", e.aprobado() ? "Identidad verificada" : "No pudimos verificar tu identidad",
                e.aprobado() ? "¡Listo! Ya puedes vincular tus bancos." : "Motivo: " + e.motivo() + ". Contacta a soporte.");
    }

    @ApplicationModuleListener
    void consentimiento(ConsentimientoActivado e) {
        n.push(e.idUsuario(), "seguridad", e.institucion() + " vinculado", "Autorizaste a Ahorra+ a leer tus saldos en " + e.institucion()
                + (e.permiteDebitoTdc() ? " y a debitar para pagar tus TDC" : "") + ". Puedes revocarlo cuando quieras.");
    }

    @ApplicationModuleListener
    void consentimientoRevocado(ConsentimientoRevocado e) {
        n.push(e.idUsuario(), "seguridad", e.institucion() + " desvinculado", "Ahorra+ ya no tiene acceso a " + e.institucion() + ".");
    }
}
