package ve.impulsatech.ahorramas.pagos.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.DiferenciaConciliacion;
import ve.impulsatech.ahorramas.pagos.api.PagosApi;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.*;
import ve.impulsatech.ahorramas.pagos.dominio.Conciliador;
import ve.impulsatech.ahorramas.pagos.dominio.EstadoPago;
import ve.impulsatech.ahorramas.pagos.dominio.PagoTdc;

/**
 * F2 · paso 8: conciliación diaria contra el archivo de liquidación del suiche (R-19).
 * Diferencia → CON_DIFERENCIA → reverso solicitado al suiche → caso en backoffice y aviso al usuario.
 */
@Service
class ServicioConciliacion implements PagosApi {

    private static final Logger log = LoggerFactory.getLogger(ServicioConciliacion.class);

    private final Repositorio repo;
    private final Suiche suiche;
    private final ProcesadorPago procesador;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;

    ServicioConciliacion(Repositorio repo, Suiche suiche, ProcesadorPago procesador, Reloj reloj, Auditoria auditoria,
                         ApplicationEventPublisher eventos, TransactionTemplate tx) {
        this.repo = repo;
        this.suiche = suiche;
        this.procesador = procesador;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.tx = tx;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumenPago> pagosPorEstado(String estado, int limite) {
        EstadoPago e = estado == null || estado.isBlank() ? null : EstadoPago.valueOf(estado.trim().toUpperCase());
        return repo.pagosPorEstado(e, limite).stream()
                .map(p -> new ResumenPago(p.idPago(), p.idUsuario(), p.estado().name(), p.montoCts(), p.comisionCobrada(), p.referenciaSuiche(),
                        p.claveIdempotencia(), p.fechaHoraCreacion(), p.motivoRechazo()))
                .toList();
    }

    @Override
    public synchronized ResumenConciliacion conciliar(LocalDate fecha) {
        var previo = repo.archivo(fecha);
        if (previo.isPresent() && !"recibido".equals(previo.get().estatus())) {
            return conciliacion(fecha);   // idempotente: una conciliación por día
        }
        ArchivoLiquidacion archivo = suiche.liquidacion(fecha);
        if (!archivo.hashValido()) {
            log.warn("El hash del archivo de liquidación {} no coincide con su contenido", archivo.nombreArchivo());
        }
        long idArchivo = previo.map(ArchivoGuardado::idArchivo)
                .orElseGet(() -> tx.execute(s -> repo.insertarArchivo(archivo, reloj.ahora(), "recibido")));

        OffsetDateTime desde = fecha.atStartOfDay(reloj.zona()).toOffsetDateTime();
        OffsetDateTime hasta = fecha.plusDays(1).atStartOfDay(reloj.zona()).toOffsetDateTime();
        Map<Long, PagoTdc> pagos = new LinkedHashMap<>();
        repo.pagosCapturadosEntre(desde, hasta).forEach(p -> pagos.put(p.idPago(), p));
        repo.pagosPorReferencias(archivo.registros().stream().map(RegistroArchivo::referenciaSuiche).toList(),
                archivo.registros().stream().map(RegistroArchivo::referencia).toList()).forEach(p -> pagos.putIfAbsent(p.idPago(), p));

        List<Conciliador.PagoAConciliar> aConciliar = new ArrayList<>();
        pagos.values().stream()
                .filter(p -> p.estado() == EstadoPago.CAPTURADO || p.estado() == EstadoPago.REVERSADO || p.estado() == EstadoPago.RECHAZADO)
                .forEach(p -> aConciliar.add(new Conciliador.PagoAConciliar(p.idPago(), p.claveIdempotencia(), p.referenciaSuiche(), p.montoCts(), p.estado())));
        List<Conciliador.RegistroLiquidacion> registros = archivo.registros().stream()
                .map(r -> new Conciliador.RegistroLiquidacion(r.linea(), r.referenciaSuiche(), r.referencia(), r.montoCts(), r.estado())).toList();

        List<Conciliador.Resultado> resultados = Conciliador.conciliar(aConciliar, registros);
        boolean conDiferencias = false;
        for (Conciliador.Resultado r : resultados) {
            tx.executeWithoutResult(s -> repo.insertarResultado(idArchivo, fecha, r));
            PagoTdc pago = r.idPago() == null ? null : pagos.get(r.idPago());
            if (!r.diferencia()) {
                if (pago != null && pago.estado() == EstadoPago.CAPTURADO) {
                    procesador.transicion(pago.idPago(), "sistema", x -> x.pasarA(EstadoPago.CONCILIADO));
                }
                continue;
            }
            conDiferencias = true;
            if (pago != null && pago.estado() == EstadoPago.CAPTURADO) {
                procesador.transicion(pago.idPago(), "sistema", x -> x.pasarA(EstadoPago.CON_DIFERENCIA));
                if (procesador.reversarEnSuiche(pago, "Diferencia en conciliación: " + r.tipo())) {
                    procesador.transicion(pago.idPago(), "suiche", x -> x.pasarA(EstadoPago.REVERSADO));
                }
            }
            Long idUsuario = pago == null ? null : pago.idUsuario();
            String refSuiche = pago != null ? pago.referenciaSuiche()
                    : registros.stream().filter(x -> x.linea() == r.linea()).map(Conciliador.RegistroLiquidacion::referenciaSuiche).findFirst().orElse(null);
            tx.executeWithoutResult(s -> {
                auditoria.registrar(Actor.sistema, null, "CONCILIACION_DIFERENCIA", "RESULTADO_CONCILIACION", r.idPago(),
                        Map.of("resultado", r.tipo().name(), "diferenciaCts", r.diferenciaCts() == null ? 0 : r.diferenciaCts()));
                eventos.publishEvent(new DiferenciaConciliacion(r.idPago(), idUsuario, r.tipo().name(),
                        r.diferenciaCts() == null ? 0 : r.diferenciaCts(), refSuiche, fecha));
            });
        }
        String estatus = conDiferencias ? "con_diferencias" : "procesado";
        tx.executeWithoutResult(s -> {
            repo.actualizarEstatusArchivo(idArchivo, estatus);
            auditoria.registrar(Actor.sistema, null, "CONCILIACION_EJECUTADA", "ARCHIVO_LIQUIDACION", idArchivo,
                    Map.of("fecha", fecha.toString(), "registros", archivo.totalRegistros(), "resultados", resultados.size()));
        });
        return conciliacion(fecha, archivo.hashValido());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenConciliacion conciliacion(LocalDate fecha) {
        return conciliacion(fecha, true);
    }

    private ResumenConciliacion conciliacion(LocalDate fecha, boolean hashValido) {
        var a = repo.archivo(fecha).orElse(null);
        if (a == null) {
            return new ResumenConciliacion(fecha, null, null, false, "sin_archivo", 0, 0, 0, 0, List.of());
        }
        List<Conciliador.Resultado> res = repo.resultados(a.idArchivo());
        int conciliados = (int) res.stream().filter(r -> !r.diferencia()).count();
        return new ResumenConciliacion(fecha, a.nombreArchivo(), a.hashArchivo(), hashValido, a.estatus(), a.totalRegistros(), a.montoTotalCts(),
                conciliados, res.size() - conciliados,
                res.stream().map(r -> new LineaResultado(r.idPago(), r.linea(), r.tipo().name(), r.diferenciaCts())).toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ResumenDia resumenDelDia(long idUsuario, LocalDate fecha) {
        var uso = repo.usoDelDia(idUsuario, fecha.atStartOfDay(reloj.zona()).toOffsetDateTime(),
                fecha.plusDays(1).atStartOfDay(reloj.zona()).toOffsetDateTime());
        return new ResumenDia(uso.montoCts(), uso.operaciones());
    }

    @Override
    public int resolverEnVerificacion() {
        int n = 0;
        for (PagoTdc p : repo.pagosPorEstado(EstadoPago.EN_VERIFICACION, 200)) {
            try {
                procesador.verificar(p);
                n++;
            } catch (RuntimeException e) {
                log.warn("No se pudo verificar el pago {}: {}", p.idPago(), e.getMessage());
            }
        }
        for (PagoTdc p : repo.pagosPorEstado(EstadoPago.AUTORIZADO, 200)) {
            // autorizados huérfanos (p. ej. caída del núcleo entre autorización y captura)
            if (p.fechaHoraCreacion().isBefore(reloj.ahora().minusMinutes(2))) {
                try {
                    procesador.procesar(p.idPago());
                    n++;
                } catch (RuntimeException e) {
                    log.warn("No se pudo capturar el pago {}: {}", p.idPago(), e.getMessage());
                }
            }
        }
        return n;
    }
}
