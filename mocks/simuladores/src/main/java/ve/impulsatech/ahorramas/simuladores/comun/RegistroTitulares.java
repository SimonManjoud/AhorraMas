package ve.impulsatech.ahorramas.simuladores.comun;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Identidad civil conocida por los simuladores: cuando el proveedor KYC verifica a una persona,
 * su nombre queda asociado a la cédula, y los bancos simulados lo usan como titular de las
 * cuentas y tarjetas de esa cédula (en la vida real el banco ya conoce a su cliente).
 */
@Component
public class RegistroTitulares {

    private final Map<String, String> nombres = new ConcurrentHashMap<>();

    /** @param cedula en formato normalizado (V12345678 / E12345678). */
    public void registrar(String cedula, String primerNombre, String primerApellido) {
        String nombre = (texto(primerNombre) + " " + texto(primerApellido)).trim();
        if (cedula != null && !nombre.isEmpty()) {
            nombres.put(cedula, nombre);
        }
    }

    public Optional<String> nombreDe(String cedula) {
        return Optional.ofNullable(cedula == null ? null : nombres.get(cedula));
    }

    /** "V" + número, a partir de la nacionalidad que envía el backend ("V", "E", "venezolano"…). */
    public static String cedula(String nacionalidad, long numero) {
        String n = nacionalidad == null || nacionalidad.isBlank() ? "V" : nacionalidad.trim().substring(0, 1);
        return n.toUpperCase(Locale.ROOT) + numero;
    }

    private static String texto(String s) {
        return s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
    }
}
