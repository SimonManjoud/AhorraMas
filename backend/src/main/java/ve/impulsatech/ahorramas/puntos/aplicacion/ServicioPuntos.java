package ve.impulsatech.ahorramas.puntos.aplicacion;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.puntos.api.EventosPuntos.NivelAlcanzado;
import ve.impulsatech.ahorramas.puntos.api.EventosPuntos.PuntosAcreditados;
import ve.impulsatech.ahorramas.puntos.aplicacion.puerto.entrada.CasosDeUsoPuntos.*;
import ve.impulsatech.ahorramas.puntos.aplicacion.puerto.salida.RepositorioPuntos;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles.Nivel;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles.Recompensa;
import ve.impulsatech.ahorramas.puntos.dominio.TransaccionPuntos;

/** Puntos Ahorro: el saldo se calcula a partir del ledger, nunca se guarda (R-21). */
@Service
class ServicioPuntos implements ConsultarPuntos, CanjearRecompensa, AcreditarPuntos {

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final RepositorioPuntos repo;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;

    ServicioPuntos(RepositorioPuntos repo, Reloj reloj, Auditoria auditoria, ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Override
    @Transactional
    public void abrirCuenta(long idUsuario) {
        repo.cuentaUsuario(idUsuario, reloj.ahora());
        if (repo.nivelVigente(idUsuario).isEmpty()) {
            repo.cambiarNivel(idUsuario, Niveles.nivelPara(0, repo.niveles()).idNivel(), reloj.ahora());
        }
    }

    @Override
    @Transactional
    public void acreditarDesdeSistema(long idUsuario, long puntos, String concepto, String descripcion, String claveOrigen) {
        acreditar(idUsuario, repo.cuentaSistema(), puntos, concepto, descripcion, claveOrigen);
    }

    /** Pago puntual: los puntos los financia el banco aliado (cuenta de fondeo, R-20). */
    @Override
    @Transactional
    public void acreditarDesdeBanco(long idUsuario, long idInstitucion, long puntos, String descripcion, String claveOrigen) {
        acreditar(idUsuario, repo.cuentaFondeoBanco(idInstitucion, reloj.ahora()), puntos, "pago_puntual", descripcion, claveOrigen);
    }

    private void acreditar(long idUsuario, long origen, long puntos, String concepto, String descripcion, String clave) {
        if (puntos <= 0 || repo.existeClave(clave)) {
            return;   // idempotente: un evento nunca acredita dos veces
        }
        OffsetDateTime ahora = reloj.ahora();
        abrirCuenta(idUsuario);
        long cuenta = repo.cuentaUsuario(idUsuario, ahora);
        repo.registrar(TransaccionPuntos.transferencia(origen, cuenta, puntos, concepto, descripcion, clave, ahora));
        eventos.publishEvent(new PuntosAcreditados(idUsuario, puntos, concepto, descripcion));
        recalcularNivel(idUsuario, cuenta);
    }

    private void recalcularNivel(long idUsuario, long cuenta) {
        List<Nivel> niveles = repo.niveles();
        Nivel corresponde = Niveles.nivelPara(repo.totalCreditos(cuenta), niveles);
        var actual = repo.nivelVigente(idUsuario);
        if (actual.isEmpty() || actual.get().nivel().orden() < corresponde.orden()) {
            repo.cambiarNivel(idUsuario, corresponde.idNivel(), reloj.ahora());
            if (actual.isPresent()) {
                eventos.publishEvent(new NivelAlcanzado(idUsuario, corresponde.nombre()));
            }
        }
    }

    @Override
    @Transactional
    public ResumenPuntos resumen(long idUsuario) {
        abrirCuenta(idUsuario);
        long cuenta = repo.cuentaUsuario(idUsuario, reloj.ahora());
        long ganados = repo.totalCreditos(cuenta);
        var vigente = repo.nivelVigente(idUsuario).orElseThrow();
        var siguiente = Niveles.siguiente(vigente.nivel(), repo.niveles())
                .map(n -> new SiguienteNivel(n.nombre(), n.puntosMinimos(), Math.max(0, n.puntosMinimos() - ganados))).orElse(null);
        return new ResumenPuntos(repo.saldo(cuenta), ganados, new NivelVista(vigente.nivel().nombre(), vigente.desde()), siguiente);
    }

    @Override
    @Transactional
    public List<MovimientoPuntos> movimientos(long idUsuario) {
        long cuenta = repo.cuentaUsuario(idUsuario, reloj.ahora());
        return repo.movimientos(cuenta, 100).stream()
                .map(m -> new MovimientoPuntos(m.idTransaccion(), m.fechaHora(), m.concepto(), m.descripcion(), m.puntos())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecompensaVista> recompensas() {
        return repo.recompensas().stream().filter(r -> r.vigente(reloj.hoy()))
                .map(r -> new RecompensaVista(r.idRecompensa(), r.descripcion(), r.costoPuntos(), r.comercio(), r.nivelMinimo().nombre(),
                        r.stockDisponible())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CanjeVista> canjes(long idUsuario) {
        return repo.canjes(idUsuario).stream()
                .map(c -> new CanjeVista(c.idCanje(), c.recompensa(), c.codigoCanje(), c.puntosCanjeados(), c.estatus(), c.fechaHora())).toList();
    }

    /** R-22: débito al usuario, crédito al comercio aliado, en una sola transacción de partida doble. */
    @Override
    @Transactional
    public CanjeVista canjear(long idUsuario, long idRecompensa) {
        OffsetDateTime ahora = reloj.ahora();
        abrirCuenta(idUsuario);
        Recompensa r = repo.recompensaParaActualizar(idRecompensa).orElseThrow(() -> ErrorNegocio.noEncontrado("Recompensa"));
        long cuenta = repo.cuentaUsuario(idUsuario, ahora);
        Niveles.validarCanje(r, repo.saldo(cuenta), repo.nivelVigente(idUsuario).orElseThrow().nivel(), reloj.hoy());
        long cuentaComercio = repo.cuentaComercio(r.idComercio(), ahora);
        String codigo = codigo();
        long idTx = repo.registrar(TransaccionPuntos.transferencia(cuenta, cuentaComercio, r.costoPuntos(), "canje",
                "Canje: " + r.descripcion(), "canje:" + codigo, ahora));
        if (r.stockDisponible() != null) {
            repo.descontarStock(idRecompensa);
        }
        long idCanje = repo.insertarCanje(idRecompensa, idUsuario, idTx, r.costoPuntos(), codigo, ahora);
        auditoria.registrar(Actor.usuario, idUsuario, "CANJE", "CANJE", idCanje, Map.of("recompensa", idRecompensa, "puntos", r.costoPuntos()));
        return new CanjeVista(idCanje, r.descripcion(), codigo, r.costoPuntos(), "emitido", ahora);
    }

    private static String codigo() {
        StringBuilder sb = new StringBuilder("AH-");
        for (int i = 0; i < 8; i++) {
            sb.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
