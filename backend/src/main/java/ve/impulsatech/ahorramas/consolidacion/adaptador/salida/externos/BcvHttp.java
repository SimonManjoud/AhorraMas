package ve.impulsatech.ahorramas.consolidacion.adaptador.salida.externos;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.Bcv;

/** Adaptador BCV (tasa oficial e inflación). */
@Component
class BcvHttp implements Bcv {

    private final RestClient http;

    BcvHttp(@Qualifier("simuladores") RestClient http) {
        this.http = http;
    }

    record TasaDto(String moneda, LocalDate fecha, long valorBsX10000) {
    }

    record InflacionDto(int anio, int mes, int variacionMensualPb) {
    }

    @Override
    public TasaBcv tasaHoy() {
        TasaDto t = http.get().uri("/bcv/tasas/hoy?moneda=USD").retrieve().body(TasaDto.class);
        return new TasaBcv(t.fecha(), t.valorBsX10000());
    }

    @Override
    public List<TasaBcv> tasas(LocalDate desde, LocalDate hasta) {
        List<TasaDto> l = http.get().uri("/bcv/tasas?moneda=USD&desde={d}&hasta={h}", desde, hasta).retrieve()
                .body(new ParameterizedTypeReference<List<TasaDto>>() { });
        return l == null ? List.of() : l.stream().map(t -> new TasaBcv(t.fecha(), t.valorBsX10000())).toList();
    }

    @Override
    public Optional<Integer> inflacionPb(int anio, int mes) {
        try {
            InflacionDto i = http.get().uri("/bcv/inflacion?anio={a}&mes={m}", anio, mes).retrieve().body(InflacionDto.class);
            return Optional.ofNullable(i).map(InflacionDto::variacionMensualPb);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }
}
