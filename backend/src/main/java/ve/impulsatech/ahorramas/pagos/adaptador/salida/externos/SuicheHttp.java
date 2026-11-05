package ve.impulsatech.ahorramas.pagos.adaptador.salida.externos;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import ve.impulsatech.ahorramas.compartido.cifrado.ServicioCifrado;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.*;

/**
 * Adaptador del suiche interbancario (capa anticorrupción). Timeouts y errores 5xx se traducen a
 * {@link SuicheSinRespuesta}: el resultado es desconocido y se debe consultar, nunca asumir fallo.
 */
@Component
class SuicheHttp implements Suiche {

    private final RestClient http;
    private final JsonMapper json;

    SuicheHttp(@Qualifier("suiche") RestClient http, JsonMapper json) {
        this.http = http;
        this.json = json;
    }

    @Override
    public Beneficiario consultarBeneficiario(String bancoEmisor, String refTarjeta) {
        try {
            return http.post().uri("/suiche/beneficiarios/consulta").body(Map.of("bancoEmisor", bancoEmisor, "refTarjeta", refTarjeta))
                    .retrieve().body(Beneficiario.class);
        } catch (HttpClientErrorException e) {
            throw ErrorNegocio.regla("TARJETA_NO_EXISTE", "El suiche no reconoce la tarjeta destino");
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw ErrorNegocio.servicioExterno("El suiche no está disponible para confirmar el beneficiario");
        }
    }

    @Override
    public RespuestaSuiche autorizar(SolicitudAutorizacion s) {
        return enviar("/suiche/autorizaciones", s);
    }

    @Override
    public RespuestaSuiche capturar(String referencia, String codigoAutorizacion) {
        return enviar("/suiche/capturas", Map.of("referencia", referencia, "codigoAutorizacion", codigoAutorizacion == null ? "" : codigoAutorizacion));
    }

    @Override
    public RespuestaSuiche reversar(String referencia, String motivo) {
        return enviar("/suiche/reversos", Map.of("referencia", referencia, "motivo", motivo));
    }

    @Override
    public OperacionSuiche consultar(String referencia) {
        try {
            return http.get().uri("/suiche/operaciones/{r}", referencia).retrieve().body(OperacionSuiche.class);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return new OperacionSuiche(referencia, "no_existe", null, null, null);
            }
            throw new SuicheSinRespuesta("Consulta rechazada: " + e.getStatusCode(), e);
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new SuicheSinRespuesta("El suiche no respondió la consulta", e);
        }
    }

    record ArchivoDto(LocalDate fechaLiquidacion, String nombreArchivo, String hashArchivo, int totalRegistros, long montoTotalCts,
                      List<Map<String, Object>> registros) {
    }

    @Override
    public ArchivoLiquidacion liquidacion(LocalDate fecha) {
        ArchivoDto a;
        try {
            a = http.get().uri("/suiche/liquidaciones/{f}", fecha).retrieve().body(ArchivoDto.class);
        } catch (ResourceAccessException | HttpServerErrorException | HttpClientErrorException e) {
            throw ErrorNegocio.servicioExterno("No se pudo obtener el archivo de liquidación del suiche");
        }
        List<Map<String, Object>> crudos = a.registros() == null ? List.of() : a.registros();
        // Integridad: SHA-256 del arreglo de registros en JSON compacto con llaves en orden alfabético.
        String canonico = json.writeValueAsString(crudos.stream().map(TreeMap::new).toList());
        boolean hashValido = ServicioCifrado.sha256Hex(canonico).equalsIgnoreCase(a.hashArchivo());
        List<RegistroArchivo> registros = crudos.stream().map(r -> new RegistroArchivo(
                ((Number) r.get("linea")).intValue(), (String) r.get("referencia"), (String) r.get("referenciaSuiche"),
                ((Number) r.get("montoCts")).longValue(), (String) r.get("estado"),
                OffsetDateTime.parse((String) r.get("fechaHoraOperacion")))).toList();
        return new ArchivoLiquidacion(a.fechaLiquidacion(), a.nombreArchivo(), a.hashArchivo(), hashValido, a.totalRegistros(), a.montoTotalCts(),
                registros);
    }

    private RespuestaSuiche enviar(String ruta, Object cuerpo) {
        try {
            return http.post().uri(ruta).body(cuerpo).retrieve().body(RespuestaSuiche.class);
        } catch (HttpClientErrorException e) {
            // 4xx: el suiche respondió y no procesó la operación → rechazo explícito.
            return new RespuestaSuiche(null, "rechazado", String.valueOf(e.getStatusCode().value()), null, null, motivo(e));
        } catch (ResourceAccessException | HttpServerErrorException e) {
            throw new SuicheSinRespuesta("Sin respuesta del suiche en " + ruta, e);
        }
    }

    private String motivo(HttpClientErrorException e) {
        try {
            Map<String, Object> m = json.readValue(e.getResponseBodyAsString(), new tools.jackson.core.type.TypeReference<Map<String, Object>>() { });
            return String.valueOf(m.getOrDefault("mensaje", "Operación rechazada por el suiche"));
        } catch (RuntimeException ex) {
            return "Operación rechazada por el suiche";
        }
    }
}
