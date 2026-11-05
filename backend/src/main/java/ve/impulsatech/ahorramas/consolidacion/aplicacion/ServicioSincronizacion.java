package ve.impulsatech.ahorramas.consolidacion.aplicacion;

import java.time.OffsetDateTime;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.api.EventosConsolidacion.MovimientoRegistrado;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.entrada.CasosDeUsoConsolidacion.SincronizarCuentas;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.*;
import ve.impulsatech.ahorramas.consolidacion.dominio.Consentimiento;
import ve.impulsatech.ahorramas.consolidacion.dominio.Institucion;

/**
 * F1 · paso 6 (Worker): lee cuentas, saldos y movimientos con el token del consentimiento y
 * publica cada movimiento nuevo (lo consumen las reglas de ahorro del Cofre).
 */
@Service
class ServicioSincronizacion implements SincronizarCuentas {

    private static final Logger log = LoggerFactory.getLogger(ServicioSincronizacion.class);
    static final int DIAS_HISTORIA = 60;

    private final Repositorio repo;
    private final OpenBanking openBanking;
    private final BovedaSecretos boveda;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;

    ServicioSincronizacion(Repositorio repo, OpenBanking openBanking, BovedaSecretos boveda, Reloj reloj, Auditoria auditoria,
                           ApplicationEventPublisher eventos, TransactionTemplate tx) {
        this.repo = repo;
        this.openBanking = openBanking;
        this.boveda = boveda;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.tx = tx;
    }

    @Override
    public void sincronizarUsuario(long idUsuario) {
        OffsetDateTime ahora = reloj.ahora();
        repo.consentimientos(idUsuario).stream().filter(c -> c.activo(ahora)).forEach(this::sincronizar);
    }

    @Override
    public int sincronizarTodos() {
        var activos = repo.consentimientosActivos();
        activos.forEach(this::sincronizar);
        return activos.size();
    }

    /** Sincroniza un consentimiento en su propia transacción (un banco caído no afecta a los demás). */
    void sincronizar(Consentimiento c) {
        try {
            tx.executeWithoutResult(s -> sincronizarEnTransaccion(c));
        } catch (ErrorNegocio e) {
            if ("TOKEN_RECHAZADO".equals(e.codigo())) {
                tx.executeWithoutResult(s -> {
                    repo.guardar(c.vencer());
                    repo.inactivarCuentas(c.idConsentimiento());
                    auditoria.registrar(Actor.sistema, null, "CONSENTIMIENTO_VENCIDO", "CONSENTIMIENTO", c.idConsentimiento(),
                            Map.of("motivo", "token rechazado por la institución"));
                });
            } else {
                log.warn("Sincronización fallida del consentimiento {}: {}", c.idConsentimiento(), e.getMessage());
            }
        } catch (RuntimeException e) {
            log.warn("Sincronización fallida del consentimiento {}: {}", c.idConsentimiento(), e.getMessage());
        }
    }

    private void sincronizarEnTransaccion(Consentimiento c) {
        Institucion inst = repo.institucion(c.idInstitucion()).orElseThrow();
        String token = boveda.leer(c.refTokenBoveda());
        OffsetDateTime ahora = reloj.ahora();
        int nuevos = 0;
        for (CuentaExterna ce : openBanking.cuentas(inst.codigo(), token)) {
            long idCuenta = repo.guardarCuenta(c.idUsuario(), c.idConsentimiento(), inst, ce, ahora);
            SaldoExterno saldo = openBanking.saldo(inst.codigo(), token, ce.refCuenta());
            repo.registrarSaldo(idCuenta, saldo.saldoCts(), saldo.saldoDisponibleCts(), ahora);
            Moneda moneda = Moneda.de(ce.moneda());
            for (MovimientoExterno m : openBanking.movimientos(inst.codigo(), token, ce.refCuenta(), reloj.hoy().minusDays(DIAS_HISTORIA))) {
                var id = repo.registrarMovimiento(idCuenta, m);
                if (id.isPresent()) {
                    nuevos++;
                    eventos.publishEvent(new MovimientoRegistrado(c.idUsuario(), id.get(), idCuenta, m.naturaleza(),
                            clasificacion(m), m.montoCts(), moneda.name(), m.fechaHora(), m.descripcion()));
                }
            }
        }
        if (nuevos > 0) {
            log.debug("Consentimiento {}: {} movimientos nuevos", c.idConsentimiento(), nuevos);
        }
    }

    static String clasificacion(MovimientoExterno m) {
        String s = m.clasificacionSugerida();
        if (s != null && (s.equals("ingreso") || s.equals("gasto") || s.equals("transf_propia") || s.equals("pago_tdc"))) {
            return s;
        }
        return "credito".equals(m.naturaleza()) ? "ingreso" : "gasto";
    }
}
