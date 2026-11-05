package ve.impulsatech.ahorramas.identidad.dominio;

import java.time.OffsetDateTime;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** DISPOSITIVO: ligado al usuario; soporta saldo rápido (F3) y revocación automática. */
public record Dispositivo(
        Long idDispositivo,
        long idUsuario,
        String huella,
        String plataforma,
        String modelo,
        OffsetDateTime fechaRegistro,
        boolean biometriaVinculada,
        boolean saldoRapidoActivo,
        OffsetDateTime fechaActivSaldoRapido,
        String estatus,
        OffsetDateTime fechaRevocacion,
        String motivoRevocacion) {

    public enum Motivo { cambio_sim, root, robo, tres_fallas, usuario;

        public String codigo() {
            return this == tres_fallas ? "3_fallas" : name();
        }

        public static Motivo de(String codigo) {
            if ("3_fallas".equals(codigo)) {
                return tres_fallas;
            }
            try {
                return valueOf(codigo);
            } catch (IllegalArgumentException | NullPointerException e) {
                throw ErrorNegocio.validacion("Motivo de revocación inválido");
            }
        }
    }

    public static Dispositivo nuevo(long idUsuario, String huellaHash, String plataforma, String modelo, OffsetDateTime ahora) {
        String plat = switch (plataforma == null ? "" : plataforma) {
            case "iOS", "ios", "IOS" -> "iOS";
            case "Android", "android" -> "Android";
            default -> "Otro";
        };
        String mod = modelo == null || modelo.isBlank() ? "desconocido" : modelo.trim();
        return new Dispositivo(null, idUsuario, huellaHash, plat, mod.length() > 80 ? mod.substring(0, 80) : mod, ahora,
                false, false, null, "activo", null, null);
    }

    public boolean activo() {
        return "activo".equals(estatus);
    }

    public boolean reportadoRobado() {
        return !activo() && "robo".equals(motivoRevocacion);
    }

    public Dispositivo revocar(Motivo motivo, OffsetDateTime ahora) {
        return new Dispositivo(idDispositivo, idUsuario, huella, plataforma, modelo, fechaRegistro, biometriaVinculada,
                false, fechaActivSaldoRapido, "revocado", ahora, motivo.codigo());
    }

    public Dispositivo reactivar() {
        if (reportadoRobado()) {
            throw ErrorNegocio.prohibido("Este dispositivo fue reportado como robado. Contacta a soporte.");
        }
        return new Dispositivo(idDispositivo, idUsuario, huella, plataforma, modelo, fechaRegistro, biometriaVinculada,
                false, fechaActivSaldoRapido, "activo", null, null);
    }

    public Dispositivo conSaldoRapido(boolean activo, OffsetDateTime ahora) {
        if (!activo()) {
            throw ErrorNegocio.prohibido("Dispositivo revocado");
        }
        return new Dispositivo(idDispositivo, idUsuario, huella, plataforma, modelo, fechaRegistro, biometriaVinculada,
                activo, activo ? ahora : fechaActivSaldoRapido, estatus, fechaRevocacion, motivoRevocacion);
    }
}
