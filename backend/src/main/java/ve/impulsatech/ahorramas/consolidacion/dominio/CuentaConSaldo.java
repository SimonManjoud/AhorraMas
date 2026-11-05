package ve.impulsatech.ahorramas.consolidacion.dominio;

import java.time.OffsetDateTime;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;

/** CUENTA_VINCULADA con su último SALDO_CUENTA sincronizado. */
public record CuentaConSaldo(
        long idCuenta,
        long idUsuario,
        long idConsentimiento,
        long idInstitucion,
        String codigoInstitucion,
        String nombreInstitucion,
        String tipoInstitucion,
        String refCuentaExterna,
        String ultimos4,
        String tipo,
        Moneda moneda,
        String alias,
        boolean soloLectura,
        String estatus,
        Long saldoCts,
        OffsetDateTime fechaSaldo) {

    public boolean activa() {
        return "activa".equals(estatus);
    }

    public long saldoOCero() {
        return saldoCts == null ? 0 : saldoCts;
    }

    /** R-04: las cuentas de plataformas cripto (y las billeteras) siempre son de solo lectura. */
    public static boolean soloLecturaPara(String tipoInstitucion, String tipoCuenta) {
        return !"banco".equals(tipoInstitucion) || "billetera".equals(tipoCuenta);
    }
}
