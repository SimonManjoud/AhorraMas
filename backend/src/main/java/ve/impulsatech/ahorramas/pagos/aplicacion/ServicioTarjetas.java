package ve.impulsatech.ahorramas.pagos.aplicacion;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.entrada.CasosDeUsoPagos.*;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.*;

/** Tarjetas de crédito descubiertas por consentimiento (alias + últimos 4; nunca PAN, R-02). */
@Service
class ServicioTarjetas implements GestionarTarjetas {

    private static final Logger log = LoggerFactory.getLogger(ServicioTarjetas.class);

    private final Repositorio repo;
    private final EmisorTarjetas emisor;
    private final Suiche suiche;
    private final CuentasApi cuentas;
    private final Reloj reloj;
    private final Auditoria auditoria;

    ServicioTarjetas(Repositorio repo, EmisorTarjetas emisor, Suiche suiche, CuentasApi cuentas, Reloj reloj, Auditoria auditoria) {
        this.repo = repo;
        this.emisor = emisor;
        this.suiche = suiche;
        this.cuentas = cuentas;
        this.reloj = reloj;
        this.auditoria = auditoria;
    }

    @Override
    @Transactional
    public List<VistaTarjeta> tarjetas(long idUsuario) {
        for (CuentasApi.AccesoBanco acceso : cuentas.accesosBancos(idUsuario)) {
            try {
                for (TarjetaExterna t : emisor.tarjetas(acceso.codigoInstitucion(), acceso.accessToken())) {
                    long id = repo.guardarTarjeta(idUsuario, acceso.idInstitucion(), t, reloj.ahora());
                    if (t.estadoCuenta() != null) {
                        repo.guardarEstadoCuenta(id, t.estadoCuenta());
                    }
                }
            } catch (RuntimeException e) {
                log.warn("No se pudieron leer las tarjetas de {}: {}", acceso.codigoInstitucion(), e.getMessage());
            }
        }
        return repo.tarjetas(idUsuario).stream().filter(t -> "activa".equals(t.estatus())).map(this::vista).toList();
    }

    /** F2 · paso 2: el suiche devuelve el titular; el usuario confirma nombre y últimos 4. */
    @Override
    @Transactional
    public BeneficiarioConfirmable confirmarBeneficiario(long idUsuario, long idTarjeta) {
        TarjetaGuardada t = repo.tarjeta(idTarjeta).filter(x -> x.idUsuario() == idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Tarjeta"));
        Beneficiario b = suiche.consultarBeneficiario(cuentas.institucion(t.idInstitucion()).codigo(), t.refTarjetaEmisor());
        repo.confirmarTitular(idTarjeta, b.titular());
        auditoria.registrar(Actor.usuario, idUsuario, "BENEFICIARIO_CONSULTADO", "TARJETA_CREDITO", idTarjeta, Map.of("ultimos4", b.ultimos4()));
        return new BeneficiarioConfirmable(b.titular(), b.ultimos4());
    }

    private VistaTarjeta vista(TarjetaGuardada t) {
        EstadoCuentaExterno e = t.estadoCuenta();
        return new VistaTarjeta(t.idTarjeta(), t.idInstitucion(), cuentas.institucion(t.idInstitucion()).nombre(), t.ultimos4(), t.alias(),
                t.titularConfirmado(), t.esPropia(),
                e == null ? null : new EstadoCuentaVista(e.fechaCorte(), e.fechaLimitePago(), e.saldoTotalCts(), e.pagoMinimoCts()),
                t.marca());
    }
}
