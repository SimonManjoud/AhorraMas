package ve.impulsatech.ahorramas.consolidacion.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;

/** Consultas sobre cuentas vinculadas, accesos bancarios por consentimiento y alianzas. */
public interface CuentasApi {

    Optional<CuentaInfo> cuenta(long idUsuario, long idCuenta);

    /** Cuentas en Bs con consentimiento activo que permite débito para pagar TDC (R-16). */
    List<CuentaInfo> cuentasDebitables(long idUsuario);

    /** Token de acceso (leído de la bóveda) para operar con la institución; exige débito si se pide. */
    AccesoBanco acceso(long idUsuario, long idInstitucion, boolean requiereDebito);

    /** Bancos (no cripto) con consentimiento de lectura activo. */
    List<AccesoBanco> accesosBancos(long idUsuario);

    Totales totales(long idUsuario);

    /** Rasgos financieros seudonimizables para la IA (sin nombre, cédula ni cuentas). */
    RasgosFinancieros rasgos(long idUsuario);

    Optional<Alianza> alianzaVigente(long idInstitucion);

    InstitucionInfo institucion(long idInstitucion);

    record CuentaInfo(long idCuenta, long idUsuario, long idInstitucion, String codigoInstitucion, String nombreInstitucion,
                      String refCuentaExterna, String ultimos4, String tipo, Moneda moneda, String alias, long saldoCts,
                      boolean soloLectura, boolean activa) {
    }

    record AccesoBanco(long idInstitucion, String codigoInstitucion, String nombre, String accessToken) {
    }

    record Totales(long totalBsCts, long totalUsdCts, OffsetDateTime actualizadoEn) {
    }

    record RasgosFinancieros(long ingresoMensualUsdCts, int volatilidadSaldoPb, long saldoTotalUsdCts) {
    }

    record Alianza(long idInstitucion, long feeClientePuntualCts, int puntosPorPagoPuntual) {
    }

    record InstitucionInfo(long idInstitucion, String codigo, String nombre, String tipo) {
    }
}
