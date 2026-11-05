package ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.api.CuentasApi.Alianza;
import ve.impulsatech.ahorramas.consolidacion.dominio.Consentimiento;
import ve.impulsatech.ahorramas.consolidacion.dominio.CuentaConSaldo;
import ve.impulsatech.ahorramas.consolidacion.dominio.Institucion;

/** Puertos de salida del módulo de consolidación. */
public final class PuertosConsolidacion {

    private PuertosConsolidacion() {
    }

    /** Open Banking simulado (un adaptador por banco/plataforma en el Hub de integraciones). */
    public interface OpenBanking {
        String urlAutorizacion(String codigoInstitucion, String estado, List<String> alcances, String loginHint);

        String canjearCodigo(String codigoInstitucion, String codigo);

        List<CuentaExterna> cuentas(String codigoInstitucion, String token);

        SaldoExterno saldo(String codigoInstitucion, String token, String refCuenta);

        List<MovimientoExterno> movimientos(String codigoInstitucion, String token, String refCuenta, LocalDate desde);

        void revocar(String codigoInstitucion, String token);
    }

    /** Fuente oficial: Banco Central de Venezuela. */
    public interface Bcv {
        TasaBcv tasaHoy();

        List<TasaBcv> tasas(LocalDate desde, LocalDate hasta);

        Optional<Integer> inflacionPb(int anio, int mes);
    }

    /** Bóveda de secretos (Vault): tokens de consentimiento; la BD solo guarda la referencia (R-02). */
    public interface BovedaSecretos {
        String guardar(String secreto);

        String leer(String referencia);

        void destruir(String referencia);
    }

    /** Persistencia del esquema consolidacion. */
    public interface Repositorio {
        List<Institucion> instituciones();

        Optional<Institucion> institucion(long idInstitucion);

        Consentimiento guardar(Consentimiento c);

        Optional<Consentimiento> consentimiento(long id);

        Optional<Consentimiento> consentimientoPorEstado(String estadoOauth);

        List<Consentimiento> consentimientos(long idUsuario);

        List<Consentimiento> consentimientosActivos();

        void vencerPendientes(long idUsuario, long idInstitucion);

        long guardarCuenta(long idUsuario, long idConsentimiento, Institucion inst, CuentaExterna c, OffsetDateTime ahora);

        void inactivarCuentas(long idConsentimiento);

        void registrarSaldo(long idCuenta, long saldoCts, Long disponibleCts, OffsetDateTime fecha);

        /** Inserta si no existe; devuelve el id nuevo o vacío si ya estaba (idempotente por referencia externa). */
        Optional<Long> registrarMovimiento(long idCuenta, MovimientoExterno m);

        List<CuentaConSaldo> cuentas(long idUsuario);

        Optional<CuentaConSaldo> cuenta(long idCuenta);

        void cambiarAlias(long idCuenta, String alias);

        List<MovimientoGuardado> movimientos(long idCuenta, int limite);

        List<MovimientoGuardado> movimientosUsuarioDesde(long idUsuario, OffsetDateTime desde);

        Optional<TasaBcv> tasaGuardada(LocalDate fecha);

        Optional<TasaBcv> ultimaTasaHasta(LocalDate fecha);

        void guardarTasa(TasaBcv tasa, OffsetDateTime obtenida);

        List<TasaBcv> tasasGuardadas(LocalDate desde, LocalDate hasta);

        Optional<Integer> inflacionGuardada(int anio, int mes);

        void guardarInflacion(int anio, int mes, int pb, LocalDate publicacion);

        Optional<Alianza> alianzaVigente(long idInstitucion, LocalDate hoy);
    }

    public record CuentaExterna(String refCuenta, String ultimos4, String tipo, String moneda, String titular) {
    }

    public record SaldoExterno(String refCuenta, String moneda, long saldoCts, Long saldoDisponibleCts, OffsetDateTime fechaHora) {
    }

    public record MovimientoExterno(String refMovimiento, OffsetDateTime fechaHora, String naturaleza, long montoCts,
                                    String descripcion, String clasificacionSugerida) {
    }

    public record MovimientoGuardado(long idMovimiento, long idCuenta, OffsetDateTime fechaHora, String naturaleza, String clasificacion,
                                     long montoCts, String moneda, String descripcion) {
    }
}
