package ve.impulsatech.ahorramas.consolidacion.aplicacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi;
import ve.impulsatech.ahorramas.consolidacion.api.TasasApi;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.entrada.CasosDeUsoConsolidacion.ConsultarConsolidado;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.BovedaSecretos;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.MovimientoGuardado;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.Repositorio;
import ve.impulsatech.ahorramas.consolidacion.dominio.Consentimiento;
import ve.impulsatech.ahorramas.consolidacion.dominio.CuentaConSaldo;
import ve.impulsatech.ahorramas.consolidacion.dominio.Institucion;
import ve.impulsatech.ahorramas.consolidacion.dominio.PosicionGlobal;

/** Posición global y API de cuentas para los demás módulos. */
@Service
class ServicioConsolidado implements ConsultarConsolidado, CuentasApi {

    private final Repositorio repo;
    private final TasasApi tasas;
    private final BovedaSecretos boveda;
    private final Reloj reloj;

    ServicioConsolidado(Repositorio repo, TasasApi tasas, BovedaSecretos boveda, Reloj reloj) {
        this.repo = repo;
        this.tasas = tasas;
        this.boveda = boveda;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public PosicionGlobal posicion(long idUsuario) {
        return PosicionGlobal.calcular(repo.cuentas(idUsuario), tasas.tasaHoy());
    }

    @Override
    @Transactional
    public void cambiarAlias(long idUsuario, long idCuenta, String alias) {
        propia(idUsuario, idCuenta);
        String a = alias == null ? null : alias.trim();
        repo.cambiarAlias(idCuenta, a == null || a.isEmpty() ? null : a.length() > 40 ? a.substring(0, 40) : a);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimientoGuardado> movimientos(long idUsuario, long idCuenta) {
        propia(idUsuario, idCuenta);
        return repo.movimientos(idCuenta, 100);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CuentaInfo> cuenta(long idUsuario, long idCuenta) {
        return repo.cuenta(idCuenta).filter(c -> c.idUsuario() == idUsuario).map(ServicioConsolidado::info);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CuentaInfo> cuentasDebitables(long idUsuario) {
        OffsetDateTime ahora = reloj.ahora();
        return repo.cuentas(idUsuario).stream()
                .filter(c -> c.activa() && !c.soloLectura() && c.moneda() == Moneda.VES && "banco".equals(c.tipoInstitucion()))
                .filter(c -> repo.consentimiento(c.idConsentimiento()).map(k -> k.activo(ahora) && k.permiteDebitoTdc()).orElse(false))
                .map(ServicioConsolidado::info)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccesoBanco acceso(long idUsuario, long idInstitucion, boolean requiereDebito) {
        OffsetDateTime ahora = reloj.ahora();
        Consentimiento c = repo.consentimientos(idUsuario).stream()
                .filter(x -> x.idInstitucion() == idInstitucion && x.activo(ahora)).findFirst()
                .orElseThrow(() -> ErrorNegocio.regla("R-16", "No tienes un consentimiento activo con esa institución"));
        if (requiereDebito && !c.permiteDebitoTdc()) {
            throw ErrorNegocio.regla("R-16", "El consentimiento con esa institución no permite débitos para pagar TDC");
        }
        Institucion inst = repo.institucion(idInstitucion).orElseThrow();
        return new AccesoBanco(idInstitucion, inst.codigo(), inst.nombre(), boveda.leer(c.refTokenBoveda()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccesoBanco> accesosBancos(long idUsuario) {
        OffsetDateTime ahora = reloj.ahora();
        return repo.consentimientos(idUsuario).stream()
                .filter(c -> c.activo(ahora))
                .map(c -> Map.entry(c, repo.institucion(c.idInstitucion()).orElseThrow()))
                .filter(e -> e.getValue().esBanco())
                .map(e -> new AccesoBanco(e.getValue().idInstitucion(), e.getValue().codigo(), e.getValue().nombre(),
                        boveda.leer(e.getKey().refTokenBoveda())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Totales totales(long idUsuario) {
        PosicionGlobal p = posicion(idUsuario);
        return new Totales(p.totalBsCts(), p.totalUsdCts(), p.actualizadoEn() == null ? reloj.ahora() : p.actualizadoEn());
    }

    /**
     * Rasgos para la IA: ingreso mensual (últimos 30 días, en USD) y volatilidad = desviación de los
     * flujos netos diarios respecto del saldo total, en puntos básicos.
     */
    @Override
    @Transactional(readOnly = true)
    public RasgosFinancieros rasgos(long idUsuario) {
        TasaBcv tasa = tasas.tasaHoy();
        OffsetDateTime desde = reloj.ahora().minusDays(30);
        List<MovimientoGuardado> movs = repo.movimientosUsuarioDesde(idUsuario, desde);
        long ingresoUsd = 0;
        Map<LocalDate, Long> netoDiario = new TreeMap<>();
        for (MovimientoGuardado m : movs) {
            long usd = tasa.aUsd(m.montoCts(), Moneda.de(m.moneda()));
            if ("ingreso".equals(m.clasificacion())) {
                ingresoUsd += usd;
            }
            netoDiario.merge(m.fechaHora().toLocalDate(), "credito".equals(m.naturaleza()) ? usd : -usd, Long::sum);
        }
        long saldoUsd = Math.max(totales(idUsuario).totalUsdCts(), 1);
        double media = netoDiario.values().stream().mapToLong(Long::longValue).average().orElse(0);
        double varianza = netoDiario.values().stream().mapToDouble(v -> (v - media) * (v - media)).average().orElse(0);
        int volatilidad = (int) Math.min(10_000, Math.round(Math.sqrt(varianza) * 10_000 / saldoUsd));
        return new RasgosFinancieros(ingresoUsd, volatilidad, saldoUsd);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Alianza> alianzaVigente(long idInstitucion) {
        return repo.alianzaVigente(idInstitucion, reloj.hoy());
    }

    @Override
    @Transactional(readOnly = true)
    public InstitucionInfo institucion(long idInstitucion) {
        Institucion i = repo.institucion(idInstitucion).orElseThrow(() -> ErrorNegocio.noEncontrado("Institución"));
        return new InstitucionInfo(i.idInstitucion(), i.codigo(), i.nombre(), i.tipo());
    }

    private CuentaConSaldo propia(long idUsuario, long idCuenta) {
        return repo.cuenta(idCuenta).filter(c -> c.idUsuario() == idUsuario).orElseThrow(() -> ErrorNegocio.noEncontrado("Cuenta"));
    }

    private static CuentaInfo info(CuentaConSaldo c) {
        return new CuentaInfo(c.idCuenta(), c.idUsuario(), c.idInstitucion(), c.codigoInstitucion(), c.nombreInstitucion(),
                c.refCuentaExterna(), c.ultimos4(), c.tipo(), c.moneda(), c.alias(), c.saldoOCero(), c.soloLectura(), c.activa());
    }
}
