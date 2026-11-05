package ve.impulsatech.ahorramas.pagos.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.PagoSolicitado;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.entrada.CasosDeUsoPagos.*;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.Repositorio;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.TarjetaGuardada;
import ve.impulsatech.ahorramas.pagos.dominio.ControlesPago;
import ve.impulsatech.ahorramas.pagos.dominio.EstadoPago;
import ve.impulsatech.ahorramas.pagos.dominio.PagoTdc;
import ve.impulsatech.ahorramas.pagos.dominio.TarifaComision;

/**
 * F2 · Pago de TDC interbancario. Idempotencia por {@code Idempotency-Key} (R-15): un reintento
 * nunca crea otro pago. Controles: consentimiento con débito, beneficiario confirmado, step-up
 * (en el adaptador web), límite diario y antifraude (R-16).
 */
@Service
class ServicioPagos implements PagarTarjeta {

    private final Repositorio repo;
    private final ProcesadorPago procesador;
    private final CuentasApi cuentas;
    private final UsuariosApi usuarios;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate tx;
    private final int maxIntentosVentana;

    ServicioPagos(Repositorio repo, ProcesadorPago procesador, CuentasApi cuentas, UsuariosApi usuarios, Reloj reloj, Auditoria auditoria,
                  ApplicationEventPublisher eventos, TransactionTemplate tx,
                  @Value("${ahorra.pagos.max-intentos-ventana:3}") int maxIntentosVentana) {
        this.repo = repo;
        this.procesador = procesador;
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
        this.tx = tx;
        this.maxIntentosVentana = maxIntentosVentana;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaOrigen> cuentasOrigen(long idUsuario) {
        return cuentas.cuentasDebitables(idUsuario).stream()
                .map(c -> new CuentaOrigen(c.idCuenta(), c.nombreInstitucion(), c.ultimos4(), c.alias(), c.saldoCts())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Cotizacion cotizar(long montoCts) {
        Montos.exigirPositivo(montoCts, "montoCts");
        long comision = tarifa().calcular(montoCts);
        return new Cotizacion(montoCts, comision, montoCts + comision);
    }

    @Override
    public ResultadoPago pagar(long idUsuario, String idempotencyKey, ComandoPago c) {
        String clave = idempotencyKey == null ? null : idempotencyKey.trim();
        if (clave == null || clave.isEmpty()) {
            throw new ErrorNegocio(ErrorNegocio.Tipo.IDEMPOTENCY_KEY_REQUERIDA, "Todo pago requiere la cabecera Idempotency-Key");
        }
        var existente = repo.pagoPorClave(clave);
        if (existente.isPresent()) {
            return repetido(idUsuario, existente.get());
        }
        usuarios.exigirKycAprobado(idUsuario);
        TarjetaGuardada tarjeta = repo.tarjeta(c.idTarjeta()).filter(t -> t.idUsuario() == idUsuario && "activa".equals(t.estatus()))
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Tarjeta"));
        CuentasApi.CuentaInfo origen = cuentas.cuentasDebitables(idUsuario).stream().filter(x -> x.idCuenta() == c.idCuentaOrigen()).findFirst()
                .orElseThrow(() -> ErrorNegocio.regla("R-16", "La cuenta de origen debe ser en Bs y tener un consentimiento activo que permita débito"));
        cuentas.acceso(idUsuario, origen.idInstitucion(), true);   // R-16: consentimiento con débito vigente
        PagoTdc nuevo = PagoTdc.nuevo(idUsuario, tarjeta.idTarjeta(), origen.idCuenta(), tarjeta.idEstadoCuenta(), tarifa(), clave, c.montoCts(),
                c.beneficiarioConfirmado(), reloj.ahora());

        PagoTdc creado = tx.execute(s -> {
            repo.bloquearUsuario(idUsuario);   // serializa los pagos del usuario: el límite diario no se puede saltar en paralelo
            var limite = usuarios.limiteVigente(idUsuario);
            var uso = usoDelDia(idUsuario);
            ControlesPago.validarLimite(limite.montoMaxDiarioCts(), limite.maxOperacionesDiarias(), uso.montoCts(), uso.operaciones(), nuevo.montoCts());
            ControlesPago.validarVelocidad(repo.intentosDesde(idUsuario, reloj.ahora().minusMinutes(ControlesPago.MINUTOS_VENTANA)), maxIntentosVentana);
            var insertado = repo.insertarPago(nuevo);
            if (insertado.isEmpty()) {
                return null;   // carrera con otro request de la misma clave: gana el primero
            }
            PagoTdc p = insertado.get();
            repo.insertarHistorial(p.idPago(), null, EstadoPago.PENDIENTE, "usuario", null, p.fechaHoraCreacion());
            auditoria.registrar(Actor.usuario, idUsuario, "PAGO_PENDIENTE", "PAGO_TDC", p.idPago(),
                    Map.of("montoCts", p.montoCts(), "comisionCts", p.comisionCts(), "idempotencyKey", clave));
            eventos.publishEvent(new PagoSolicitado(idUsuario, p.idPago(), p.montoCts(), p.fechaHoraCreacion()));
            return p;
        });
        if (creado == null) {
            return repetido(idUsuario, repo.pagoPorClave(clave).orElseThrow());
        }
        PagoTdc fin = procesador.procesar(creado.idPago());
        return new ResultadoPago(vista(fin, true), true);
    }

    private ResultadoPago repetido(long idUsuario, PagoTdc p) {
        if (p.idUsuario() != idUsuario) {
            throw ErrorNegocio.conflicto("La Idempotency-Key ya fue usada");
        }
        return new ResultadoPago(vista(p, true), false);
    }

    @Override
    @Transactional(readOnly = true)
    public VistaPago obtener(long idUsuario, long idPago) {
        PagoTdc p = repo.pago(idPago).filter(x -> x.idUsuario() == idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Pago"));
        return vista(p, true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VistaPago> listar(long idUsuario) {
        return repo.pagos(idUsuario, 100).stream().map(p -> vista(p, false)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UsoLimites limites(long idUsuario) {
        var l = usuarios.limiteVigente(idUsuario);
        var uso = usoDelDia(idUsuario);
        return new UsoLimites(l.montoMaxDiarioCts(), l.maxOperacionesDiarias(), uso.montoCts(), uso.operaciones());
    }

    private ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.UsoDiario usoDelDia(long idUsuario) {
        LocalDate hoy = reloj.hoy();
        return repo.usoDelDia(idUsuario, hoy.atStartOfDay(reloj.zona()).toOffsetDateTime(), hoy.plusDays(1).atStartOfDay(reloj.zona()).toOffsetDateTime());
    }

    private TarifaComision tarifa() {
        return repo.tarifaVigente(reloj.hoy()).orElseThrow(() -> ErrorNegocio.regla("R-17", "No hay una tarifa de comisión vigente"));
    }

    VistaPago vista(PagoTdc p, boolean conHistorial) {
        String ultimos4 = repo.tarjeta(p.idTarjeta()).map(TarjetaGuardada::ultimos4).orElse("****");
        List<HistorialVista> historial = conHistorial
                ? repo.historial(p.idPago()).stream()
                .map(h -> new HistorialVista(h.secuencia(), h.estadoAnterior(), h.estadoNuevo(), h.fechaHora(), h.actor())).toList()
                : List.of();
        return new VistaPago(p.idPago(), p.estado().name(), p.montoCts(), p.comisionCobrada(), p.referenciaSuiche(), p.codigoAutorizacion(),
                p.esPuntual(), p.motivoRechazo(), p.fechaHoraCreacion(), mensaje(p, ultimos4), ultimos4, historial);
    }

    static String mensaje(PagoTdc p, String ultimos4) {
        return switch (p.estado()) {
            case CAPTURADO, CONCILIADO -> "Pago de " + Montos.formatear(p.montoCts(), Moneda.VES) + " aplicado a tu TDC terminada en " + ultimos4
                    + (Boolean.TRUE.equals(p.esPuntual()) ? ". ¡Pagaste a tiempo!" : ".");
            case EN_VERIFICACION -> "Tu pago está en proceso: lo estamos confirmando con el suiche. No lo repitas; te avisaremos.";
            case RECHAZADO -> "El pago fue rechazado: " + p.motivoRechazo() + ". No se hizo ningún débito ni se cobró comisión.";
            case REVERSADO -> "El pago fue reversado: el monto vuelve a tu cuenta de origen.";
            case CON_DIFERENCIA -> "Detectamos una diferencia en la conciliación. Ya abrimos un caso y te mantendremos informado.";
            case PENDIENTE, AUTORIZADO -> "Estamos procesando tu pago…";
        };
    }
}
