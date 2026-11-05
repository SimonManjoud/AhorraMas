package ve.impulsatech.ahorramas.compartido.seguridad;

/** Nivel de autenticación reforzada: PIN/huella para cambios; PIN + OTP para pagos (F2, R-16). */
public enum NivelStepUp {
    PIN("pin"), PIN_OTP("pin_otp");

    private final String codigo;

    NivelStepUp(String codigo) {
        this.codigo = codigo;
    }

    public String codigo() {
        return codigo;
    }

    public boolean cubre(NivelStepUp requerido) {
        return this.ordinal() >= requerido.ordinal();
    }

    public static NivelStepUp de(String codigo) {
        for (NivelStepUp n : values()) {
            if (n.codigo.equals(codigo)) {
                return n;
            }
        }
        throw new IllegalArgumentException("Nivel de step-up desconocido: " + codigo);
    }
}
