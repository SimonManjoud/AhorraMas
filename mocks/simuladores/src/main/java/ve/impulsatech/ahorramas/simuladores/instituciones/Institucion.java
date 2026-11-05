package ve.impulsatech.ahorramas.simuladores.instituciones;

import java.util.List;
import java.util.Optional;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;

/**
 * Institución del catálogo fijo (códigos ficticios, no son bancos reales).
 * {@code tipoPlataforma} es {@code null} para los bancos.
 */
public record Institucion(String codigo, String nombre, String tipo, String tipoPlataforma) {

    public static final String BANCO = "banco";
    public static final String CRIPTO = "plataforma_cripto";

    public static final List<Institucion> CATALOGO = List.of(
            new Institucion("0901", "Banco Ávila", BANCO, null),
            new Institucion("0902", "Banco Orinoco", BANCO, null),
            new Institucion("0903", "Banco Catatumbo", BANCO, null),
            new Institucion("0904", "Banco Roraima", BANCO, null),
            new Institucion("C001", "CriptoAndes Exchange", CRIPTO, "exchange"),
            new Institucion("C002", "Billetera Tepuy", CRIPTO, "billetera"));

    public boolean esBanco() {
        return BANCO.equals(tipo);
    }

    /** Bancos que emiten tarjeta de crédito al usuario. */
    public boolean emiteTdc() {
        return codigo.equals("0902") || codigo.equals("0904");
    }

    public static Optional<Institucion> buscar(String codigo) {
        return CATALOGO.stream().filter(i -> i.codigo.equals(codigo)).findFirst();
    }

    public static Institucion obtener(String codigo) {
        return buscar(codigo).orElseThrow(() -> ApiException.noEncontrado("INSTITUCION_NO_EXISTE",
                "No existe la institución '" + codigo + "'"));
    }
}
