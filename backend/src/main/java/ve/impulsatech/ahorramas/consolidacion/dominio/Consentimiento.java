package ve.impulsatech.ahorramas.consolidacion.dominio;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/**
 * CONSENTIMIENTO revocable otorgado por el usuario en su propio banco (F1). Alcance mínimo:
 * lectura de saldos y movimientos; débito solo para pagar TDC y solo en bancos (R-03).
 */
public record Consentimiento(
        Long idConsentimiento,
        long idUsuario,
        long idInstitucion,
        OffsetDateTime fechaOtorgamiento,
        OffsetDateTime fechaVencimiento,
        OffsetDateTime fechaRevocacion,
        String estatus,
        boolean permiteLectura,
        boolean permiteDebitoTdc,
        String refTokenBoveda,
        String motivoRevocacion,
        String estadoOauth) {

    public static final int DIAS_VIGENCIA = 90;

    public static Consentimiento solicitar(long idUsuario, Institucion institucion, boolean debitoSolicitado, String estadoOauth,
                                           OffsetDateTime ahora) {
        boolean debito = debitoSolicitado && institucion.esBanco();   // R-03 / R-04
        return new Consentimiento(null, idUsuario, institucion.idInstitucion(), ahora, ahora.plusDays(DIAS_VIGENCIA), null,
                "pendiente", true, debito, null, null, estadoOauth);
    }

    public List<String> alcances() {
        List<String> s = new ArrayList<>(List.of("saldos:leer", "movimientos:leer"));
        if (permiteDebitoTdc) {
            s.add("debito:tdc");
        }
        return s;
    }

    public Consentimiento activar(String refToken, OffsetDateTime ahora) {
        if (!pendiente()) {
            throw ErrorNegocio.conflicto("El consentimiento ya no está pendiente");
        }
        return new Consentimiento(idConsentimiento, idUsuario, idInstitucion, ahora, ahora.plusDays(DIAS_VIGENCIA), null, "activo",
                permiteLectura, permiteDebitoTdc, refToken, null, null);
    }

    public Consentimiento revocar(String motivo, OffsetDateTime ahora) {
        if ("revocado".equals(estatus)) {
            return this;
        }
        return new Consentimiento(idConsentimiento, idUsuario, idInstitucion, fechaOtorgamiento, fechaVencimiento, ahora, "revocado",
                permiteLectura, permiteDebitoTdc, refTokenBoveda, motivo, null);
    }

    public Consentimiento vencer() {
        return new Consentimiento(idConsentimiento, idUsuario, idInstitucion, fechaOtorgamiento, fechaVencimiento, null, "vencido",
                permiteLectura, permiteDebitoTdc, refTokenBoveda, motivoRevocacion, null);
    }

    public boolean pendiente() {
        return "pendiente".equals(estatus);
    }

    public boolean activo(OffsetDateTime ahora) {
        return "activo".equals(estatus) && ahora.isBefore(fechaVencimiento);
    }
}
