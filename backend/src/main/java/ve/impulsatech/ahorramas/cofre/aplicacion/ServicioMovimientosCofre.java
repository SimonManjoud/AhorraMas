package ve.impulsatech.ahorramas.cofre.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.AporteRegistrado;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.MetaCumplida;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.SelloRoto;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.entrada.CasosDeUsoCofre.*;
import ve.impulsatech.ahorramas.cofre.aplicacion.puerto.salida.RepositorioCofre;
import ve.impulsatech.ahorramas.cofre.dominio.Aporte;
import ve.impulsatech.ahorramas.cofre.dominio.MetaAhorro;
import ve.impulsatech.ahorramas.cofre.dominio.PuntosPorAporte;
import ve.impulsatech.ahorramas.cofre.dominio.Racha;
import ve.impulsatech.ahorramas.cofre.dominio.SelloCompromiso;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria;
import ve.impulsatech.ahorramas.compartido.auditoria.Auditoria.Actor;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;
import ve.impulsatech.ahorramas.identidad.api.UsuariosApi;

/** Aportes y retiros del cofre: ledger inmutable (R-10) + racha + puntos + sello. */
@Service
class ServicioMovimientosCofre implements MoverCofre {

    static final long MAXIMO_APORTE_USD_CTS = 10_000_000L; // $100.000 por asiento

    private final RepositorioCofre repo;
    private final ServicioMetas metas;
    private final ServicioCuotas cuotas;
    private final ServicioRacha rachas;
    private final TasasApi tasas;
    private final CuentasApi cuentas;
    private final UsuariosApi usuarios;
    private final Reloj reloj;
    private final Auditoria auditoria;
    private final ApplicationEventPublisher eventos;

    ServicioMovimientosCofre(RepositorioCofre repo, ServicioMetas metas, ServicioCuotas cuotas, ServicioRacha rachas, TasasApi tasas,
                             CuentasApi cuentas, UsuariosApi usuarios, Reloj reloj, Auditoria auditoria, ApplicationEventPublisher eventos) {
        this.repo = repo;
        this.metas = metas;
        this.cuotas = cuotas;
        this.rachas = rachas;
        this.tasas = tasas;
        this.cuentas = cuentas;
        this.usuarios = usuarios;
        this.reloj = reloj;
        this.auditoria = auditoria;
        this.eventos = eventos;
    }

    @Override
    @Transactional
    public ResultadoAporte aportar(long idUsuario, long idMeta, long montoUsdCts, String origen, Long idCuentaOrigen) {
        usuarios.exigirKycAprobado(idUsuario);
        String o = origen == null ? "manual" : origen;
        if (!o.equals("manual") && !o.equals("cuota_del_dia")) {
            throw ErrorNegocio.validacion("Origen inválido (manual o cuota_del_dia)");
        }
        validarMonto(montoUsdCts);
        MetaAhorro meta = metaBloqueada(idUsuario, idMeta);
        if (idCuentaOrigen != null) {
            cuentas.cuenta(idUsuario, idCuentaOrigen).filter(CuentasApi.CuentaInfo::activa)
                    .orElseThrow(() -> ErrorNegocio.noEncontrado("Cuenta de origen"));
        }
        Long idCuota = "cuota_del_dia".equals(o) ? cuotas.cuotaHoy(meta).idCuota() : null;
        return registrar(meta, montoUsdCts, o, idCuentaOrigen, null, idCuota, null);
    }

    /** Aporte generado por una regla automática (idempotente por clave de origen). */
    @Transactional
    void aportarPorRegla(long idRegla, long idMeta, long montoUsdCts, String claveOrigen) {
        if (montoUsdCts <= 0 || repo.existeClaveOrigen(claveOrigen)) {
            return;
        }
        MetaAhorro meta = repo.metaParaActualizar(idMeta).orElseThrow();
        if (!meta.activa()) {
            return;
        }
        registrar(meta, Math.min(montoUsdCts, MAXIMO_APORTE_USD_CTS), "regla", null, idRegla, null, claveOrigen);
    }

    private ResultadoAporte registrar(MetaAhorro meta, long montoUsdCts, String origen, Long idCuenta, Long idRegla, Long idCuota,
                                      String claveOrigen) {
        long idUsuario = meta.idUsuario();
        OffsetDateTime ahora = reloj.ahora();
        LocalDate hoy = ahora.toLocalDate();
        TasaBcv tasa = tasas.tasaDe(hoy);
        boolean primerDelDia = !repo.huboAporteDelUsuarioEntre(idUsuario, hoy.atStartOfDay(reloj.zona()).toOffsetDateTime(),
                hoy.plusDays(1).atStartOfDay(reloj.zona()).toOffsetDateTime(), -1);

        Aporte aporte = repo.insertarAporte(new Aporte(null, meta.idMeta(), idCuenta, idRegla, idCuota, ahora, "aporte", origen, montoUsdCts,
                tasa.usdABs(montoUsdCts), tasa.valorBsX10000(), claveOrigen));

        Racha racha = rachas.registrarAporte(idUsuario, hoy);
        int multiplicador = racha.tramo(repo.tramosVigentes()).multiplicadorX100();
        PuntosPorAporte.Puntos puntos = PuntosPorAporte.calcular(montoUsdCts, primerDelDia, multiplicador);
        cuotas.actualizarEstatus(meta);

        MetaAhorro actual = meta;
        long acumulado = repo.acumulado(meta.idMeta());
        if (acumulado >= meta.montoObjetivoUsdCts() && meta.activa()) {
            actual = repo.guardarMeta(meta.cerrar("cumplida", ahora));
            repo.selloActivo(meta.idMeta()).ifPresent(s -> repo.guardarSello(s.liberar()));
            eventos.publishEvent(new MetaCumplida(idUsuario, meta.idMeta(), meta.nombre(), meta.montoObjetivoUsdCts()));
        }
        auditoria.registrar("regla".equals(origen) ? Actor.sistema : Actor.usuario, idUsuario, "APORTE_COFRE", "APORTE", aporte.idAporte(),
                Map.of("meta", meta.idMeta(), "usdCts", montoUsdCts, "origen", origen));
        eventos.publishEvent(new AporteRegistrado(idUsuario, meta.idMeta(), aporte.idAporte(), meta.nombre(), montoUsdCts, origen,
                puntos.base(), puntos.bonoRacha(), multiplicador));
        return new ResultadoAporte(vista(aporte), metas.vista(actual), rachas.racha(idUsuario), puntos.total());
    }

    @Override
    @Transactional
    public ResultadoRetiro retirar(long idUsuario, long idMeta, long montoUsdCts) {
        validarMonto(montoUsdCts);
        MetaAhorro meta = metaBloqueada(idUsuario, idMeta);
        long acumulado = repo.acumulado(idMeta);
        if (montoUsdCts > acumulado) {
            throw ErrorNegocio.regla("R-08", "No puedes retirar más de lo que tienes en el cofre");
        }
        OffsetDateTime ahora = reloj.ahora();
        TasaBcv tasa = tasas.tasaDe(ahora.toLocalDate());
        Aporte retiro = repo.insertarAporte(new Aporte(null, idMeta, null, null, null, ahora, "retiro", "manual", montoUsdCts,
                tasa.usdABs(montoUsdCts), tasa.valorBsX10000(), null));

        boolean roto = false;
        var sello = repo.selloActivo(idMeta);
        if (sello.isPresent() && sello.get().seRompeCon(acumulado - montoUsdCts, ahora.toLocalDate())) {
            roto = true;
            romperSello(meta, sello.get(), ahora);
        }
        auditoria.registrar(Actor.usuario, idUsuario, "RETIRO_COFRE", "APORTE", retiro.idAporte(),
                Map.of("meta", idMeta, "usdCts", montoUsdCts, "selloRoto", roto));
        return new ResultadoRetiro(vista(retiro), metas.vista(meta), roto);
    }

    /** R-12: romper el sello → racha cerrada (pierde el multiplicador), el dinero nunca se toca. */
    void romperSello(MetaAhorro meta, SelloCompromiso sello, OffsetDateTime ahora) {
        int multiplicador = rachas.multiplicadorActual(meta.idUsuario());
        repo.guardarSello(sello.romper(ahora));
        rachas.cerrarPorSello(meta.idUsuario());
        auditoria.registrar(Actor.usuario, meta.idUsuario(), "SELLO_ROTO", "SELLO_COMPROMISO", sello.idSello());
        eventos.publishEvent(new SelloRoto(meta.idUsuario(), meta.idMeta(), meta.nombre(), multiplicador));
    }

    @Override
    @Transactional(readOnly = true)
    public List<VistaAporte> aportes(long idUsuario, long idMeta) {
        metas.propia(idUsuario, idMeta);
        return repo.aportes(idMeta).stream().map(ServicioMovimientosCofre::vista).toList().reversed();
    }

    private MetaAhorro metaBloqueada(long idUsuario, long idMeta) {
        MetaAhorro meta = repo.metaParaActualizar(idMeta).filter(m -> m.idUsuario() == idUsuario)
                .orElseThrow(() -> ErrorNegocio.noEncontrado("Meta"));
        if (!meta.activa()) {
            throw ErrorNegocio.regla("META_NO_ACTIVA", "La meta está " + meta.estatus());
        }
        return meta;
    }

    private static void validarMonto(long montoUsdCts) {
        if (montoUsdCts <= 0 || montoUsdCts > MAXIMO_APORTE_USD_CTS) {
            throw ErrorNegocio.validacion("El monto debe ser positivo y no mayor a $100.000 (en céntimos de USD)");
        }
    }

    static VistaAporte vista(Aporte a) {
        return new VistaAporte(a.idAporte(), a.fechaHora(), a.tipo(), a.origen(), a.montoUsdCts(), a.montoBsCts());
    }
}
