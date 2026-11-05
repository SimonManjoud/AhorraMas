package ve.impulsatech.ahorramas.identidad.dominio;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Locale;
import java.util.regex.Pattern;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;

/** Agregado USUARIO (R-06). La PII se cifra en el adaptador de persistencia, no aquí. */
public record Usuario(
        Long idUsuario,
        String nacionalidadDoc,
        int cedula,
        String primerNombre,
        String segundoNombre,
        String primerApellido,
        String segundoApellido,
        LocalDate fechaNacimiento,
        String correo,
        String telefonoMovil,
        long idSeudonimo,
        OffsetDateTime fechaRegistro,
        String estatus) {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern TELEFONO = Pattern.compile("^04(12|14|16|24|26|22)\\d{7}$");
    private static final Pattern PIN = Pattern.compile("^\\d{4,6}$");
    public static final int EDAD_MINIMA = 18;

    /** Crea un usuario nuevo validando las invariantes del registro. */
    public static Usuario nuevo(String nacionalidadDoc, int cedula, String primerNombre, String segundoNombre,
                                String primerApellido, String segundoApellido, LocalDate fechaNacimiento,
                                String correo, String telefono, long idSeudonimo, OffsetDateTime ahora) {
        if (!"V".equals(nacionalidadDoc) && !"E".equals(nacionalidadDoc)) {
            throw ErrorNegocio.validacion("La nacionalidad del documento debe ser V o E");
        }
        if (cedula <= 0 || cedula > 99_999_999) {
            throw ErrorNegocio.validacion("Cédula inválida");
        }
        if (vacio(primerNombre) || vacio(primerApellido)) {
            throw ErrorNegocio.validacion("Nombre y apellido son obligatorios");
        }
        if (fechaNacimiento == null || Period.between(fechaNacimiento, ahora.toLocalDate()).getYears() < EDAD_MINIMA) {
            throw ErrorNegocio.regla("R-06", "Debes ser mayor de edad para registrarte");
        }
        String correoNorm = normalizarCorreo(correo);
        if (!CORREO.matcher(correoNorm).matches()) {
            throw ErrorNegocio.validacion("Correo inválido");
        }
        String telNorm = normalizarTelefono(telefono);
        if (!TELEFONO.matcher(telNorm).matches()) {
            throw ErrorNegocio.validacion("Teléfono móvil inválido (formato 04XX1234567)");
        }
        return new Usuario(null, nacionalidadDoc, cedula, primerNombre.trim(), limpiar(segundoNombre), primerApellido.trim(),
                limpiar(segundoApellido), fechaNacimiento, correoNorm, telNorm, idSeudonimo, ahora, "activo");
    }

    public static void validarPin(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw ErrorNegocio.validacion("El PIN debe tener entre 4 y 6 dígitos");
        }
    }

    public static String normalizarCorreo(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizarTelefono(String telefono) {
        return telefono == null ? "" : telefono.replaceAll("[^0-9]", "");
    }

    public boolean activo() {
        return "activo".equals(estatus);
    }

    public String nombreCorto() {
        return primerNombre + " " + primerApellido;
    }

    public String documento() {
        return nacionalidadDoc + cedula;
    }

    public Usuario conId(long id) {
        return new Usuario(id, nacionalidadDoc, cedula, primerNombre, segundoNombre, primerApellido, segundoApellido,
                fechaNacimiento, correo, telefonoMovil, idSeudonimo, fechaRegistro, estatus);
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String limpiar(String s) {
        return vacio(s) ? null : s.trim();
    }
}
