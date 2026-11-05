package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.ColchonUsado;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.RachaCerrada;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.*;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.dominio.Racha;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;

/** Engranaje 2 · Racha de ahorro con días colchón (R-11). */
@Service
class ServicioRacha implements ConsultarRacha {

    private static final Logger log = LoggerFactory.getLogger(ServicioRacha.class);

    private final RepositorioCofre repo;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;

    ServicioRacha(RepositorioCofre repo, Reloj reloj, Auditoria auditoria, ApplicationEventPublisher eventos, TransactionTemplate tx) {
        this.repo = repo;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.tx = tx;
    }

    /** Un aporte (de cualquier origen, aunque sea el redondeo) cubre el día y mantiene/inicia la racha. */
    @Transactional
    Racha registrarAporte(long idUsuario, LocalDate fecha) {
        Racha racha = repo.rachaActivaParaActualizar(idUsuario).orElseGet(() -> repo.crearRacha(idUsuario, fecha));
        if (!racha.tieneDia(fecha)) {
            repo.agregarDiaRacha(racha.idRacha(), fecha, "aporte");
        }
        return repo.rachaActiva(idUsuario).orElseThrow();
    }

    @Transactional(readOnly = true)
    int multiplicadorActual(long idUsuario) {
        return repo.rachaActiva(idUsuario).map(r -> r.tramo(repo.tramosVigentes()).multiplicadorX100()).orElse(100);
    }

    /** R-12: romper el sello cierra la racha (se pierde el multiplicador, nunca dinero). */
    @Transactional
    void cerrarPorSello(long idUsuario) {
        repo.rachaActivaParaActualizar(idUsuario).ifPresent(r -> {
            LocalDate hoy = reloj.hoy();
            LocalDate fin = hoy.isBefore(r.fechaInicio()) ? r.fechaInicio() : hoy;
            repo.cerrarRacha(r.idRacha(), fin, "sello_roto");
            auditoria.registrar(Actor.usuario, idUsuario, "RACHA_CERRADA", "RACHA", r.idRacha(), Map.of("motivo", "sello_roto"));
            eventos.publishEvent(new RachaCerrada(idUsuario, r.idRacha(), r.diasConsecutivos(), "sello_roto"));
        });
    }

    @Override
    @Transactional(readOnly = true)
    public VistaRacha racha(long idUsuario) {
        LocalDate hoy = reloj.hoy();
        List<Racha.Tramo> tramos = repo.tramosVigentes();
        Map<LocalDate, String> recientes = new HashMap<>();
        repo.diasRachaUsuario(idUsuario, hoy.minusDays(6), hoy).forEach(d -> recientes.put(d.fecha(), d.tipo()));
        List<DiaVista> ultimos = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = hoy.minusDays(i);
            ultimos.add(new DiaVista(d, recientes.getOrDefault(d, "sin_aporte")));
        }
        boolean aporteHoy = "aporte".equals(recientes.get(hoy));
        var activa = repo.rachaActiva(idUsuario);
        if (activa.isEmpty()) {
            Racha.Tramo base = Racha.tramoPara(0, tramos);
            return new VistaRacha(null, null, 0, 0, 0, base.diasColchon(), base.multiplicadorX100(),
                    Racha.siguienteTramo(0, tramos).map(t -> new TramoVista(t.diasMinimos(), t.multiplicadorX100())).orElse(null),
                    aporteHoy, ultimos);
        }
        Racha r = activa.get();
        int dias = r.diasConsecutivos();
        return new VistaRacha(r.idRacha(), r.fechaInicio(), dias, r.diasAporte(), r.colchonesUsados(), r.colchonesDisponibles(hoy, tramos),
                r.tramo(tramos).multiplicadorX100(),
                Racha.siguienteTramo(dias, tramos).map(t -> new TramoVista(t.diasMinimos(), t.multiplicadorX100())).orElse(null),
                aporteHoy, ultimos);
    }

    /**
     * Cierre de días (worker): para cada racha activa evalúa los días sin cubrir hasta {@code hasta}.
     * Día sin aporte → consume un colchón si hay; si no, la racha se cierra.
     */
    void cerrarDiasHasta(LocalDate hasta) {
        for (Racha r : repo.rachasActivas()) {
            try {
                tx.executeWithoutResult(s -> cerrarDiasDe(r.idUsuario(), hasta));
            } catch (RuntimeException e) {
                log.warn("No se pudo cerrar los días de la racha {}: {}", r.idRacha(), e.getMessage());
            }
        }
    }

    private void cerrarDiasDe(long idUsuario, LocalDate hasta) {
        Racha racha = repo.rachaActivaParaActualizar(idUsuario).orElse(null);
        if (racha == null) {
            return;
        }
        List<Racha.Tramo> tramos = repo.tramosVigentes();
        LocalDate desde = racha.ultimoDia().map(d -> d.plusDays(1)).orElse(racha.fechaInicio());
        for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
            switch (racha.evaluarDia(d, tramos)) {
                case DIA_CUBIERTO -> { }
                case USAR_COLCHON -> {
                    repo.agregarDiaRacha(racha.idRacha(), d, "colchon");
                    List<Racha.Dia> dias = new ArrayList<>(racha.dias());
                    dias.add(new Racha.Dia(d, "colchon"));
                    racha = new Racha(racha.idRacha(), idUsuario, racha.fechaInicio(), null, null, dias);
                    eventos.publishEvent(new ColchonUsado(idUsuario, d, racha.colchonesDisponibles(d, tramos), racha.diasConsecutivos()));
                }
                case CERRAR -> {
                    LocalDate fin = d.minusDays(1).isBefore(racha.fechaInicio()) ? racha.fechaInicio() : d.minusDays(1);
                    repo.cerrarRacha(racha.idRacha(), fin, "sin_aporte");
                    auditoria.registrar(Actor.sistema, null, "RACHA_CERRADA", "RACHA", racha.idRacha(),
                            Map.of("motivo", "sin_aporte", "dias", racha.diasConsecutivos()));
                    eventos.publishEvent(new RachaCerrada(idUsuario, racha.idRacha(), racha.diasConsecutivos(), "sin_aporte"));
                    return;
                }
            }
        }
    }
}
