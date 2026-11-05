package ve.impulsatech.ahorramas.consolidacion.dominio;

/** INSTITUCION_FINANCIERA (supertipo) con subtipos BANCO y PLATAFORMA_CRIPTO. */
public record Institucion(long idInstitucion, String codigo, String nombre, String tipo, String estatusIntegracion,
                          String codigoSudeban, String tipoPlataforma) {

    public boolean esBanco() {
        return "banco".equals(tipo);
    }
}
