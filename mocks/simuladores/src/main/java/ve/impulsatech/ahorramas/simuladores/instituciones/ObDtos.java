package ve.impulsatech.ahorramas.simuladores.instituciones;

import com.fasterxml.jackson.annotation.JsonProperty;

/** DTOs de Open Banking simulado (contrato §2). Montos en céntimos ({@code long}). */
public final class ObDtos {

    private ObDtos() {
    }

    public record Cuenta(String refCuenta, String ultimos4, String tipo, String moneda, String titular) {
    }

    public record Saldo(String refCuenta, String moneda, long saldoCts, long saldoDisponibleCts, String fechaHora) {
    }

    public record Movimiento(String refMovimiento, String fechaHora, String naturaleza, long montoCts,
                             String descripcion, String clasificacionSugerida) {
    }

    public record EstadoCuenta(String fechaCorte, String fechaLimitePago, long saldoTotalCts, long pagoMinimoCts) {
    }

    /** Nunca PAN: solo alias opaco + últimos 4. */
    public record Tarjeta(String refTarjeta, String ultimos4, String titular, EstadoCuenta estadoCuenta, String marca) {
    }

    /** Respuesta OAuth (snake_case por ser estándar OAuth2). */
    public record Token(@JsonProperty("access_token") String accessToken,
                        @JsonProperty("token_type") String tokenType,
                        @JsonProperty("expires_in") long expiresIn,
                        @JsonProperty("scope") String scope) {
    }

    /** Control demo: inyectar un movimiento. */
    public record InyectarMovimiento(String cedula, String refCuenta, String naturaleza, Long montoCts,
                                     String descripcion, String clasificacion) {
    }
}
