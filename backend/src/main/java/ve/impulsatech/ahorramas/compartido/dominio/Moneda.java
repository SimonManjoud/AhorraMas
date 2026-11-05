package ve.impulsatech.ahorramas.compartido.dominio;

/** Monedas soportadas en el MVP. Todas con 2 decimales: los montos se guardan en céntimos (R-01). */
public enum Moneda {
    VES, USD, USDT;

    /** USD y stablecoins reguladas se valoran a la tasa BCV del dólar. */
    public boolean esDivisa() {
        return this != VES;
    }

    public static Moneda de(String codigo) {
        return Moneda.valueOf(codigo.trim().toUpperCase());
    }
}
