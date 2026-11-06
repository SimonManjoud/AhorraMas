package ve.impulsatech.ahorramas.pagos.dominio;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;
import ve.impulsatech.ahorramas.pagos.api.KpisApi.Bucket;
import ve.impulsatech.ahorramas.pagos.api.KpisApi.Conteo;
import ve.impulsatech.ahorramas.pagos.api.KpisApi.LatenciaTipo;
import ve.impulsatech.ahorramas.pagos.api.KpisApi.Latencias;
import ve.impulsatech.ahorramas.pagos.api.KpisApi.PagoReciente;
import ve.impulsatech.ahorramas.pagos.api.KpisApi.Resumen;

/**
 * KPIs técnicos del prototipo (tasa de aprobación, latencia p95, TPS y tasa de reversos) calculados sobre el ledger
 * inmutable: pagos, historial de estados y mensajes al suiche. Servicio de dominio puro: no conoce la base de datos
 * ni el reloj y solo usa aritmética entera (tasas en puntos básicos, latencias en milisegundos · R-01).
 *
 * <p>Definiciones (las mismas que muestra el tablero):
 * <ul>
 *   <li><b>Aprobado</b>: el suiche aprobó la captura → CAPTURADO, CONCILIADO, CON_DIFERENCIA o REVERSADO con fecha de captura.</li>
 *   <li><b>Rechazado</b>: RECHAZADO, o REVERSADO sin captura (se liberó la autorización, nunca hubo débito).</li>
 *   <li><b>En curso</b>: PENDIENTE, AUTORIZADO, EN_VERIFICACION. No entran en la tasa de aprobación.</li>
 *   <li><b>Tasa de aprobación</b> = aprobados / (aprobados + rechazados).</li>
 *   <li><b>Tasa de reversos</b> = REVERSADO / aprobados (solo puede reversarse lo que se capturó).</li>
 *   <li><b>Latencia del suiche</b> = respuesta − envío de cada mensaje (autoriz, captura, reverso, consulta) con respuesta.
 *       Los timeouts no tienen respuesta: se cuentan aparte y no sesgan el percentil.</li>
 *   <li><b>Latencia extremo a extremo</b> = creación del pago → primer estado de resultado (CAPTURADO, RECHAZADO, REVERSADO).</li>
 *   <li><b>TPS</b>: el tablero divide pagos / segundos de la ventana; aquí se mide además el pico real por segundo y por minuto.</li>
 * </ul>
 */
public final class CalculadoraKpis {

    private CalculadoraKpis() {
    }

    public record Pago(long idPago, EstadoPago estado, long montoCts, long comisionCts, OffsetDateTime creacion, OffsetDateTime captura,
                       String motivoRechazo, String referenciaSuiche) {
    }

    public record Mensaje(long idPago, String tipo, OffsetDateTime envio, OffsetDateTime respuesta, String resultado, String codigo) {
    }

    public record Transicion(long idPago, EstadoPago anterior, EstadoPago nuevo, OffsetDateTime fechaHora, String actor) {
    }

    public record Calculo(Resumen resumen, List<LatenciaTipo> latenciasPorTipo, List<Bucket> series, List<Conteo> estados,
                          List<Conteo> motivosRechazo, List<Conteo> origenReversos, List<PagoReciente> pagosRecientes) {
    }

    public static final int MAX_PAGOS_RECIENTES = 60;

    public static boolean aprobado(Pago p) {
        return p.captura() != null || p.estado() == EstadoPago.CAPTURADO || p.estado() == EstadoPago.CONCILIADO
                || p.estado() == EstadoPago.CON_DIFERENCIA;
    }

    public static boolean rechazado(Pago p) {
        return p.estado() == EstadoPago.RECHAZADO || (p.estado() == EstadoPago.REVERSADO && p.captura() == null);
    }

    public static boolean enCurso(Pago p) {
        return !aprobado(p) && !rechazado(p);
    }

    /** Tasa en puntos básicos (1 pb = 0,01 %); null cuando el denominador es cero. */
    public static Integer tasaPb(long numerador, long denominador) {
        return denominador == 0 ? null : (int) Math.round(numerador * 10_000.0 / denominador);
    }

    /** Percentil por rango más cercano sobre muestras ordenadas: p95 de 20 muestras es la 19.ª. */
    public static long percentil(long[] ordenadas, int p) {
        if (ordenadas.length == 0) {
            return 0;
        }
        int indice = (int) Math.ceil(p / 100.0 * ordenadas.length) - 1;
        return ordenadas[Math.max(0, Math.min(ordenadas.length - 1, indice))];
    }

    public static Latencias latencias(List<Long> muestras) {
        long[] v = muestras.stream().mapToLong(Long::longValue).sorted().toArray();
        return new Latencias(v.length, percentil(v, 50), percentil(v, 95), percentil(v, 99), v.length == 0 ? 0 : v[v.length - 1]);
    }

    public static Calculo calcular(OffsetDateTime desde, OffsetDateTime hasta, Duration bucket, ZoneId zona,
                                   List<Pago> pagos, List<Mensaje> mensajes, List<Transicion> transiciones) {
        // ---------------------------------------------------------------- totales
        int aprobados = 0, rechazados = 0, enCurso = 0, reversados = 0, conDiferencia = 0, conciliados = 0;
        long monto = 0, comision = 0;
        Map<EstadoPago, Integer> porEstado = new EnumMap<>(EstadoPago.class);
        Map<String, Integer> motivos = new HashMap<>();
        for (Pago p : pagos) {
            if (aprobado(p)) {
                aprobados++;
            } else if (rechazado(p)) {
                rechazados++;
            } else {
                enCurso++;
            }
            if (p.estado() == EstadoPago.REVERSADO) {
                reversados++;
            }
            if (p.estado() == EstadoPago.CON_DIFERENCIA) {
                conDiferencia++;
            }
            if (p.estado() == EstadoPago.CONCILIADO) {
                conciliados++;
            }
            monto += p.montoCts();
            comision += p.estado() == EstadoPago.RECHAZADO || p.estado() == EstadoPago.REVERSADO ? 0 : p.comisionCts();
            porEstado.merge(p.estado(), 1, Integer::sum);
            if (p.estado() == EstadoPago.RECHAZADO) {
                motivos.merge(p.motivoRechazo() == null ? "Sin motivo" : p.motivoRechazo(), 1, Integer::sum);
            }
        }

        // ---------------------------------------------------------------- latencias del suiche
        List<Long> todas = new ArrayList<>();
        Map<String, List<Long>> porTipo = new LinkedHashMap<>();
        Map<String, Integer> timeoutsPorTipo = new LinkedHashMap<>();
        Map<Long, Long> primeraAutorizacion = new HashMap<>();
        int timeouts = 0;
        for (String t : List.of("autoriz", "captura", "reverso", "consulta", "benef")) {
            porTipo.put(t, new ArrayList<>());
            timeoutsPorTipo.put(t, 0);
        }
        for (Mensaje m : mensajes) {
            porTipo.computeIfAbsent(m.tipo(), k -> new ArrayList<>());
            timeoutsPorTipo.putIfAbsent(m.tipo(), 0);
            if (m.respuesta() == null) {
                timeouts++;
                timeoutsPorTipo.merge(m.tipo(), 1, Integer::sum);
                continue;
            }
            long ms = Math.max(0, Duration.between(m.envio(), m.respuesta()).toMillis());
            todas.add(ms);
            porTipo.get(m.tipo()).add(ms);
            if ("autoriz".equals(m.tipo())) {
                primeraAutorizacion.putIfAbsent(m.idPago(), ms);
            }
        }
        List<LatenciaTipo> latenciasPorTipo = new ArrayList<>();
        porTipo.forEach((tipo, muestras) -> {
            int to = timeoutsPorTipo.getOrDefault(tipo, 0);
            if (!muestras.isEmpty() || to > 0) {
                latenciasPorTipo.add(new LatenciaTipo(tipo, muestras.size() + to, to, latencias(muestras)));
            }
        });

        // ---------------------------------------------------------------- extremo a extremo y origen de reversos
        Map<Long, OffsetDateTime> creacionPorPago = new HashMap<>();
        pagos.forEach(p -> creacionPorPago.put(p.idPago(), p.creacion()));
        Map<Long, Long> resultadoMs = new HashMap<>();
        Map<Long, OffsetDateTime> ultimaTransicion = new HashMap<>();
        Map<String, Integer> origenReversos = new LinkedHashMap<>();
        for (Transicion t : transiciones) {
            OffsetDateTime creacion = creacionPorPago.get(t.idPago());
            if (creacion == null) {
                continue;
            }
            ultimaTransicion.merge(t.idPago(), t.fechaHora(), (a, b) -> b.isAfter(a) ? b : a);
            if (t.nuevo() == EstadoPago.CAPTURADO || t.nuevo() == EstadoPago.RECHAZADO || t.nuevo() == EstadoPago.REVERSADO) {
                resultadoMs.putIfAbsent(t.idPago(), Math.max(0, Duration.between(creacion, t.fechaHora()).toMillis()));
            }
            if (t.nuevo() == EstadoPago.REVERSADO) {
                origenReversos.merge(t.anterior() == null ? "desconocido" : t.anterior().name(), 1, Integer::sum);
            }
        }

        // ---------------------------------------------------------------- picos de carga
        int pagosSegundoPico = pico(pagos, p -> p.creacion().toEpochSecond());
        int pagosMinutoPico = pico(pagos, p -> p.creacion().toEpochSecond() / 60);
        int mensajesSegundoPico = pico(mensajes, m -> m.envio().toEpochSecond());

        Resumen resumen = new Resumen(pagos.size(), aprobados, rechazados, enCurso, reversados, conDiferencia, conciliados, timeouts,
                mensajes.size(), tasaPb(aprobados, aprobados + rechazados), tasaPb(reversados, aprobados), tasaPb(timeouts, mensajes.size()),
                latencias(todas), latencias(new ArrayList<>(resultadoMs.values())),
                pagosSegundoPico, pagosMinutoPico, mensajesSegundoPico, monto, comision);

        // ---------------------------------------------------------------- serie temporal
        List<Bucket> series = serie(desde, hasta, bucket, zona, pagos, mensajes);

        List<Conteo> estados = new ArrayList<>();
        for (EstadoPago e : EstadoPago.values()) {
            int n = porEstado.getOrDefault(e, 0);
            if (n > 0) {
                estados.add(new Conteo(e.name(), n));
            }
        }
        List<PagoReciente> recientes = pagos.stream()
                .sorted(Comparator.comparing(Pago::creacion).reversed().thenComparing(Comparator.comparingLong(Pago::idPago).reversed()))
                .limit(MAX_PAGOS_RECIENTES)
                .map(p -> new PagoReciente(p.idPago(), p.estado().name(), p.montoCts(), (int) (p.montoCts() % 100), p.creacion(), p.referenciaSuiche(),
                        primeraAutorizacion.get(p.idPago()),
                        ultimaTransicion.containsKey(p.idPago()) ? Math.max(0, Duration.between(p.creacion(), ultimaTransicion.get(p.idPago())).toMillis()) : null,
                        p.motivoRechazo()))
                .toList();
        return new Calculo(resumen, latenciasPorTipo, series, estados, ordenar(motivos), ordenar(origenReversos), recientes);
    }

    private static <T> int pico(List<T> items, ToLongFunction<T> clave) {
        Map<Long, Integer> conteo = new HashMap<>();
        int max = 0;
        for (T item : items) {
            int n = conteo.merge(clave.applyAsLong(item), 1, Integer::sum);
            max = Math.max(max, n);
        }
        return max;
    }

    private static List<Conteo> ordenar(Map<String, Integer> m) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new Conteo(e.getKey(), e.getValue())).toList();
    }

    private static List<Bucket> serie(OffsetDateTime desde, OffsetDateTime hasta, Duration bucket, ZoneId zona, List<Pago> pagos, List<Mensaje> mensajes) {
        boolean porDia = bucket.compareTo(Duration.ofDays(1)) >= 0;
        OffsetDateTime inicio = porDia ? desde.atZoneSameInstant(zona).toLocalDate().atStartOfDay(zona).toOffsetDateTime()
                : desde.truncatedTo(bucket.compareTo(Duration.ofHours(1)) >= 0 ? ChronoUnit.HOURS : ChronoUnit.MINUTES);
        int n = (int) indice(inicio, hasta.minusNanos(1), bucket, porDia, zona) + 1;
        n = Math.max(1, Math.min(n, 2_000));
        int[] cPagos = new int[n], cApr = new int[n], cRech = new int[n], cCurso = new int[n], cRev = new int[n], cTo = new int[n], cPico = new int[n];
        List<List<Long>> lat = new ArrayList<>();
        List<Map<Long, Integer>> porSegundo = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lat.add(new ArrayList<>());
            porSegundo.add(new HashMap<>());
        }
        for (Pago p : pagos) {
            int i = (int) indice(inicio, p.creacion(), bucket, porDia, zona);
            if (i < 0 || i >= n) {
                continue;
            }
            cPagos[i]++;
            if (aprobado(p)) {
                cApr[i]++;
            } else if (rechazado(p)) {
                cRech[i]++;
            } else {
                cCurso[i]++;
            }
            if (p.estado() == EstadoPago.REVERSADO) {
                cRev[i]++;
            }
            int seg = porSegundo.get(i).merge(p.creacion().toEpochSecond(), 1, Integer::sum);
            cPico[i] = Math.max(cPico[i], seg);
        }
        for (Mensaje m : mensajes) {
            int i = (int) indice(inicio, m.envio(), bucket, porDia, zona);
            if (i < 0 || i >= n) {
                continue;
            }
            if (m.respuesta() == null) {
                cTo[i]++;
            } else {
                lat.get(i).add(Math.max(0, Duration.between(m.envio(), m.respuesta()).toMillis()));
            }
        }
        List<Bucket> serie = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            OffsetDateTime ini = porDia ? inicio.atZoneSameInstant(zona).toLocalDate().plusDays(i).atStartOfDay(zona).toOffsetDateTime()
                    : inicio.plus(bucket.multipliedBy(i));
            Latencias l = latencias(lat.get(i));
            serie.add(new Bucket(ini, cPagos[i], cApr[i], cRech[i], cCurso[i], cRev[i], cTo[i], tasaPb(cApr[i], cApr[i] + cRech[i]),
                    tasaPb(cRev[i], cApr[i]), l.muestras() == 0 ? null : l.p50Ms(), l.muestras() == 0 ? null : l.p95Ms(), cPico[i]));
        }
        return serie;
    }

    private static long indice(OffsetDateTime inicio, OffsetDateTime t, Duration bucket, boolean porDia, ZoneId zona) {
        if (porDia) {
            return ChronoUnit.DAYS.between(inicio.atZoneSameInstant(zona).toLocalDate(), t.atZoneSameInstant(zona).toLocalDate());
        }
        return Math.floorDiv(Duration.between(inicio, t).toMillis(), bucket.toMillis());
    }

    /** Longitud del intervalo más adecuada para una ventana: minuto hasta 3 h, hora hasta 3 días, día en adelante. */
    public static Duration bucketAutomatico(Duration ventana) {
        if (ventana.compareTo(Duration.ofHours(3)) <= 0) {
            return Duration.ofMinutes(1);
        }
        if (ventana.compareTo(Duration.ofDays(3)) <= 0) {
            return Duration.ofHours(1);
        }
        return Duration.ofDays(1);
    }

    static long[] ordenadas(long... v) {
        long[] c = Arrays.copyOf(v, v.length);
        Arrays.sort(c);
        return c;
    }
}
