package ve.impulsatech.ahorramas.pagos.adaptador.salida.externos;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.EmisorTarjetas;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.TarjetaExterna;

/** Lee las tarjetas que el banco emisor expone por consentimiento (alias + últimos 4 dígitos). */
@Component
class EmisorTarjetasHttp implements EmisorTarjetas {

    private final RestClient http;

    EmisorTarjetasHttp(@Qualifier("simuladores") RestClient http) {
        this.http = http;
    }

    @Override
    public List<TarjetaExterna> tarjetas(String codigoBanco, String token) {
        List<TarjetaExterna> r = http.get().uri("/ob/{c}/tarjetas", codigoBanco).headers(h -> h.setBearerAuth(token)).retrieve()
                .body(new ParameterizedTypeReference<List<TarjetaExterna>>() { });
        return r == null ? List.of() : r;
    }
}
