package ve.impulsatech.ahorramas.pagos.aplicacion;

import java.time.Duration;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.pagos.api.KpisApi;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.Metricas;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis;

/**
 * Tablero de KPIs del prototipo: resuelve la ventana (relativa al reloj del negocio, que en demo puede estar
 * adelantado), lee el ledger y delega el cálculo al dominio. También calcula la ventana anterior de igual
 * longitud para que el tablero muestre la variación.
 */
@Service
class ServicioKpis implements KpisApi {

    static final Duration VENTANA_MAXIMA = Duration.ofDays(92);

    private final Metricas metricas;
    private final Reloj reloj;

    ServicioKpis(Metricas metricas, Reloj reloj) {
        this.metricas = metricas;
        this.reloj = reloj;
    }

    @Override
    @Transactional(readOnly = true)
    public TableroKpis kpis(String rango, OffsetDateTime desde, OffsetDateTime hasta, String bucket) {
        OffsetDateTime ahora = reloj.ahora();
        if (hasta == null) {
            hasta = ahora;
        }
        if (desde == null) {
            desde = inicioDelRango(rango == null ? "24h" : rango.trim().toLowerCase(), hasta);
        }
        if (!desde.isBefore(hasta)) {
            throw ErrorNegocio.validacion("La ventana debe tener 'desde' anterior a 'hasta'");
        }
        Duration largo = Duration.between(desde, hasta);
        if (largo.compareTo(VENTANA_MAXIMA) > 0) {
            throw ErrorNegocio.validacion("La ventana máxima del tablero es de 92 días");
        }
        Duration b = bucket(bucket, largo);
        CalculadoraKpis.Calculo actual = calcular(desde, hasta, b);
        CalculadoraKpis.Calculo previo = calcular(desde.minus(largo), desde, b);
        return new TableroKpis(new Ventana(desde, hasta, ahora, nombre(b), largo.toSeconds(), b.toSeconds()), actual.resumen(), previo.resumen(),
                actual.latenciasPorTipo(), actual.series(), actual.estados(), actual.motivosRechazo(), actual.origenReversos(), actual.pagosRecientes());
    }

    private CalculadoraKpis.Calculo calcular(OffsetDateTime desde, OffsetDateTime hasta, Duration bucket) {
        return CalculadoraKpis.calcular(desde, hasta, bucket, reloj.zona(), metricas.pagosCreadosEntre(desde, hasta),
                metricas.mensajesDePagosCreadosEntre(desde, hasta), metricas.transicionesDePagosCreadosEntre(desde, hasta));
    }

    private OffsetDateTime inicioDelRango(String rango, OffsetDateTime hasta) {
        return switch (rango) {
            case "hoy" -> hasta.atZoneSameInstant(reloj.zona()).toLocalDate().atStartOfDay(reloj.zona()).toOffsetDateTime();
            case "1h" -> hasta.minusHours(1);
            case "24h" -> hasta.minusHours(24);
            case "7d" -> hasta.minusDays(7);
            case "30d" -> hasta.minusDays(30);
            default -> throw ErrorNegocio.validacion("Rango no soportado: usa hoy, 1h, 24h, 7d o 30d");
        };
    }

    private static Duration bucket(String bucket, Duration ventana) {
        String b = bucket == null ? "auto" : bucket.trim().toLowerCase();
        Duration d = switch (b) {
            case "auto", "" -> CalculadoraKpis.bucketAutomatico(ventana);
            case "minuto" -> Duration.ofMinutes(1);
            case "hora" -> Duration.ofHours(1);
            case "dia" -> Duration.ofDays(1);
            default -> throw ErrorNegocio.validacion("Granularidad no soportada: usa auto, minuto, hora o dia");
        };
        // protección: nunca más de 2 000 intervalos (p. ej. 30 días por minuto)
        while (ventana.dividedBy(d) > 2_000) {
            d = d.compareTo(Duration.ofHours(1)) < 0 ? Duration.ofHours(1) : Duration.ofDays(1);
        }
        return d;
    }

    private static String nombre(Duration b) {
        return b.compareTo(Duration.ofDays(1)) >= 0 ? "dia" : b.compareTo(Duration.ofHours(1)) >= 0 ? "hora" : "minuto";
    }
}
