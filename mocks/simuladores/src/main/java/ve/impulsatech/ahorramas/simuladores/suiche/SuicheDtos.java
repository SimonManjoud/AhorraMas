package ve.impulsatech.ahorramas.simuladores.suiche;

import java.util.List;

/** DTOs del suiche interbancario simulado (contrato §3). */
public final class SuicheDtos {

    private SuicheDtos() {
    }

    public record ConsultaBeneficiario(String bancoEmisor, String refTarjeta) {
    }

    public record Beneficiario(String titular, String ultimos4, String refTarjeta) {
    }

    public record Autorizacion(String referencia, String bancoOrigen, String refCuentaOrigen, String bancoEmisor,
                               String refTarjeta, Long montoCts, String moneda) {
    }

    public record Captura(String referencia, String codigoAutorizacion) {
    }

    public record Reverso(String referencia, String motivo) {
    }

    public record RespuestaSuiche(String referencia, String resultado, String codigoRespuesta,
                                  String codigoAutorizacion, String referenciaSuiche, String motivo,
                                  String fechaHora) {
    }

    public record Operacion(String referencia, String estado, String referenciaSuiche, String codigoAutorizacion,
                            Long montoCts, String fechaHora) {
    }

    public record RegistroLiquidacion(int linea, String referencia, String referenciaSuiche, long montoCts,
                                      String estado, String fechaHoraOperacion) {
    }

    public record ArchivoLiquidacion(String fechaLiquidacion, String nombreArchivo, String hashArchivo,
                                     int totalRegistros, long montoTotalCts, List<RegistroLiquidacion> registros) {
    }
}
