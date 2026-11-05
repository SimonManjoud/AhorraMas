package ve.impulsatech.ahorramas.pagos.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.PagoCapturado;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.PagoEstadoCambiado;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.*;
import ve.impulsatech.ahorramas.pagos.dominio.EstadoPago;
import ve.impulsatech.ahorramas.pagos.dominio.PagoTdc;

/**
 * Ejecuta la máquina de estados del pago contra el suiche (F2 · pasos 5–7 y excepciones).
 * Cada transición va en su propia transacción, con bloqueo de fila, historial (R-14), auditoría y evento.
 * Ante timeout el pago queda EN_VERIFICACION y se consulta con la MISMA referencia (R-15).
 */
@Component
class ProcesadorPago {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorPago.class);

    private final Repositorio repo;
    private final Suiche suiche;
    private final CuentasApi cuentas;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;

    ProcesadorPago(Repositorio repo, Suiche suiche, CuentasApi cuentas, Reloj reloj, Auditoria auditoria, ApplicationEventPublisher eventos,
                   TransactionTemplate tx) {
        this.repo = repo;
        this.suiche = suiche;
        this.cuentas = cuentas;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.tx = tx;
    }

    private record Contexto(String bancoOrigen, String refCuentaOrigen, String bancoEmisor, String refTarjeta, String ultimos4,
                            long idInstitucionEmisor, LocalDate fechaLimitePago) {
    }

    /** Avanza el pago tanto como sea posible desde su estado actual. */
    PagoTdc procesar(long idPago) {
        PagoTdc p = repo.pago(idPago).orElseThrow();
        switch (p.estado()) {
            case PENDIENTE -> autorizar(p, false);
            case AUTORIZADO -> capturar(p);
            case EN_VERIFICACION -> verificar(p);
            default -> { }
        }
        return repo.pago(idPago).orElseThrow();
    }

    private void autorizar(PagoTdc p, boolean reintento) {
        Contexto c = contexto(p);
        OffsetDateTime envio = reloj.ahora();
        RespuestaSuiche r;
        try {
            r = suiche.autorizar(new SolicitudAutorizacion(p.claveIdempotencia(), c.bancoOrigen(), c.refCuentaOrigen(), c.bancoEmisor(),
                    c.refTarjeta(), p.montoCts(), "VES"));
        } catch (SuicheSinRespuesta e) {
            repo.registrarMensaje(p.idPago(), "autoriz", envio, null, "timeout", null);
            transicion(p.idPago(), "sistema", x -> x.estado() == EstadoPago.EN_VERIFICACION ? x : x.pasarA(EstadoPago.EN_VERIFICACION));
            if (!reintento) {
                verificar(repo.pago(p.idPago()).orElseThrow());
            }
            return;
        }
        repo.registrarMensaje(p.idPago(), "autoriz", envio, reloj.ahora(), r.aprobado() ? "aprobado" : "rechazado", r.codigoRespuesta());
        if (r.aprobado()) {
            PagoTdc autorizado = transicion(p.idPago(), "suiche", x -> x.autorizado(r.codigoAutorizacion(), r.referenciaSuiche()));
            if (autorizado.estado() == EstadoPago.AUTORIZADO) {
                capturar(autorizado);
            }
        } else {
            transicion(p.idPago(), "suiche", x -> x.rechazado(motivo(r)));
        }
    }

    private void capturar(PagoTdc p) {
        Contexto c = contexto(p);
        OffsetDateTime envio = reloj.ahora();
        RespuestaSuiche r;
        try {
            r = suiche.capturar(p.claveIdempotencia(), p.codigoAutorizacion());
        } catch (SuicheSinRespuesta e) {
            repo.registrarMensaje(p.idPago(), "captura", envio, null, "timeout", null);
            transicion(p.idPago(), "sistema", x -> x.estado() == EstadoPago.EN_VERIFICACION ? x : x.pasarA(EstadoPago.EN_VERIFICACION));
            return;
        }
        repo.registrarMensaje(p.idPago(), "captura", envio, reloj.ahora(), r.aprobado() ? "aprobado" : "rechazado", r.codigoRespuesta());
        if (r.aprobado()) {
            OffsetDateTime ahora = reloj.ahora();
            transicion(p.idPago(), "suiche", x -> x.capturado(r.referenciaSuiche(), ahora, c.fechaLimitePago()));
        } else {
            // Autorizado pero el emisor rechazó la captura: se reversa la autorización y el pago queda RECHAZADO sin débito.
            reversarEnSuiche(p, "Captura rechazada por el emisor");
            transicion(p.idPago(), "suiche", x -> x.rechazado("Captura rechazada por el emisor: " + motivo(r)));
        }
    }

    /** Consulta de estado con la misma referencia; nunca se reenvía a ciegas. */
    void verificar(PagoTdc p) {
        OffsetDateTime envio = reloj.ahora();
        OperacionSuiche op;
        try {
            op = suiche.consultar(p.claveIdempotencia());
        } catch (SuicheSinRespuesta e) {
            repo.registrarMensaje(p.idPago(), "consulta", envio, null, "timeout", null);
            return; // sigue EN_VERIFICACION; el worker reintentará
        }
        repo.registrarMensaje(p.idPago(), "consulta", envio, reloj.ahora(), "aprobado", op.estado());
        switch (op.estado()) {
            case "no_existe" -> {
                if (p.codigoAutorizacion() == null) {
                    // La autorización nunca llegó al suiche: se reenvía con la MISMA referencia (el suiche es idempotente).
                    autorizar(p, true);
                } else {
                    transicion(p.idPago(), "suiche", x -> x.rechazado("El suiche no registra la operación"));
                }
            }
            case "autorizado" -> {
                PagoTdc a = transicion(p.idPago(), "suiche", x -> x.estado() == EstadoPago.AUTORIZADO ? x
                        : x.autorizado(op.codigoAutorizacion(), op.referenciaSuiche()));
                if (a.estado() == EstadoPago.AUTORIZADO) {
                    capturar(a);
                }
            }
            case "capturado" -> {
                OffsetDateTime ahora = reloj.ahora();
                LocalDate limite = contexto(p).fechaLimitePago();
                transicion(p.idPago(), "suiche", x -> x.capturado(op.referenciaSuiche(), ahora, limite));
            }
            case "rechazado" -> transicion(p.idPago(), "suiche", x -> x.rechazado("Rechazado por el suiche"));
            case "reversado" -> transicion(p.idPago(), "suiche", x -> x.pasarA(EstadoPago.REVERSADO));
            default -> log.warn("Estado desconocido del suiche para el pago {}: {}", p.idPago(), op.estado());
        }
    }

    boolean reversarEnSuiche(PagoTdc p, String motivo) {
        OffsetDateTime envio = reloj.ahora();
        try {
            RespuestaSuiche r = suiche.reversar(p.claveIdempotencia(), motivo);
            repo.registrarMensaje(p.idPago(), "reverso", envio, reloj.ahora(), r.aprobado() ? "aprobado" : "rechazado", r.codigoRespuesta());
            return r.aprobado();
        } catch (SuicheSinRespuesta e) {
            repo.registrarMensaje(p.idPago(), "reverso", envio, null, "timeout", null);
            return false;
        }
    }

    /**
     * Aplica una transición con bloqueo de fila. Si otro proceso ya movió el pago y la transición ya no
     * es válida, se devuelve el estado actual sin error (procesamiento concurrente seguro).
     */
    PagoTdc transicion(long idPago, String actor, UnaryOperator<PagoTdc> cambio) {
        return tx.execute(s -> {
            PagoTdc actual = repo.pagoParaActualizar(idPago).orElseThrow();
            PagoTdc nuevo;
            try {
                nuevo = cambio.apply(actual);
            } catch (ErrorNegocio e) {
                log.info("Transición omitida del pago {} ({}): {}", idPago, actual.estado(), e.getMessage());
                return actual;
            }
            if (nuevo.estado() == actual.estado()) {
                return actual;
            }
            OffsetDateTime ahora = reloj.ahora();
            repo.actualizarPago(nuevo, ahora);
            repo.insertarHistorial(idPago, actual.estado(), nuevo.estado(), actor, nuevo.referenciaSuiche(), ahora);
            auditoria.registrar(Actor.valueOf(actor), "suiche".equals(actor) ? null : actual.idUsuario(), "PAGO_" + nuevo.estado(),
                    "PAGO_TDC", idPago, Map.of("anterior", actual.estado().name(), "referenciaSuiche", String.valueOf(nuevo.referenciaSuiche())));
            Contexto c = contexto(nuevo);
            eventos.publishEvent(new PagoEstadoCambiado(nuevo.idUsuario(), idPago, actual.estado().name(), nuevo.estado().name(),
                    nuevo.montoCts(), c.ultimos4(), nuevo.motivoRechazo()));
            if (nuevo.estado() == EstadoPago.CAPTURADO) {
                var alianza = Boolean.TRUE.equals(nuevo.esPuntual()) ? cuentas.alianzaVigente(c.idInstitucionEmisor()) : java.util.Optional.<CuentasApi.Alianza>empty();
                eventos.publishEvent(new PagoCapturado(nuevo.idUsuario(), idPago, nuevo.montoCts(), Boolean.TRUE.equals(nuevo.esPuntual()),
                        c.idInstitucionEmisor(), alianza.map(CuentasApi.Alianza::puntosPorPagoPuntual).orElse(0),
                        alianza.map(CuentasApi.Alianza::feeClientePuntualCts).orElse(0L)));
            }
            return nuevo;
        });
    }

    private Contexto contexto(PagoTdc p) {
        TarjetaGuardada t = repo.tarjeta(p.idTarjeta()).orElseThrow();
        CuentasApi.CuentaInfo origen = cuentas.cuenta(p.idUsuario(), p.idCuentaOrigen())
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Cuenta de origen"));
        String emisor = cuentas.institucion(t.idInstitucion()).codigo();
        return new Contexto(origen.codigoInstitucion(), origen.refCuentaExterna(), emisor, t.refTarjetaEmisor(), t.ultimos4(), t.idInstitucion(),
                t.estadoCuenta() == null ? null : t.estadoCuenta().fechaLimitePago());
    }

    private static String motivo(RespuestaSuiche r) {
        return r.motivo() != null ? r.motivo() : "Código " + r.codigoRespuesta();
    }
}
