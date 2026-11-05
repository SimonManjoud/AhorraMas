package ve.impulsatech.ahorramas.simuladores.suiche;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.comun.Semilla;
import ve.impulsatech.ahorramas.simuladores.comun.Tiempo;
import ve.impulsatech.ahorramas.simuladores.instituciones.BancoService;
import ve.impulsatech.ahorramas.simuladores.instituciones.BancoService.CuentaSim;
import ve.impulsatech.ahorramas.simuladores.instituciones.Institucion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.ArchivoLiquidacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Autorizacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Beneficiario;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.Operacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.RegistroLiquidacion;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.RespuestaSuiche;

/**
 * Suiche interbancario en memoria. Cada operación se identifica por la {@code referencia}
 * (clave de idempotencia de Ahorra+): repetir un paso devuelve exactamente la misma respuesta.
 * <p>Escenarios por {@code montoCts % 100}: 13 timeout, 51 rechazo, 77 captura rechazada,
 * 98 falta en liquidación, 99 monto distinto en liquidación.
 */
@Service
public class SuicheService {

    public static final int ESC_TIMEOUT = 13;
    public static final int ESC_FONDOS = 51;
    public static final int ESC_CAPTURA_RECHAZADA = 77;
    public static final int ESC_FALTA_EN_LIQUIDACION = 98;
    public static final int ESC_MONTO_DISTINTO = 99;

    static final String AUTORIZADO = "autorizado";
    static final String CAPTURADO = "capturado";
    static final String RECHAZADO = "rechazado";
    static final String REVERSADO = "reversado";
    static final String NO_EXISTE = "no_existe";

    private static final DateTimeFormatter AAAAMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final BancoService banco;
    private final Tiempo tiempo;
    private final long retardoTimeoutMs;
    private final Map<String, OperacionSim> operaciones = new ConcurrentHashMap<>();
    private final AtomicLong secuencia = new AtomicLong();

    public SuicheService(BancoService banco, Tiempo tiempo,
                         @Value("${simulador.suiche.retardo-timeout-ms:8000}") long retardoTimeoutMs) {
        this.banco = banco;
        this.tiempo = tiempo;
        this.retardoTimeoutMs = retardoTimeoutMs;
    }

    public Beneficiario consultarBeneficiario(String bancoEmisor, String refTarjeta) {
        return banco.buscarTarjeta(refTarjeta)
                .filter(t -> t.codigo().equals(bancoEmisor))
                .map(t -> new Beneficiario(t.titular(), t.ultimos4(), t.ref()))
                .orElseThrow(() -> ApiException.noEncontrado("TARJETA_NO_EXISTE",
                        "La tarjeta no existe en el banco emisor indicado"));
    }

    public RespuestaSuiche autorizar(Autorizacion a) {
        validar(a);
        RespuestaSuiche respuesta;
        boolean simularTimeout;
        synchronized (this) {
            OperacionSim existente = operaciones.get(a.referencia());
            if (existente != null) {
                return existente.respAutorizacion; // idempotente: sin efectos ni retardo
            }
            OperacionSim op = new OperacionSim(a, secuencia.incrementAndGet(), tiempo.ahora());
            op.referenciaSuiche = "S7B-" + op.creada.toLocalDate().format(AAAAMMDD) + "-"
                    + String.format("%06d", op.numero);
            int escenario = (int) (a.montoCts() % 100);
            String motivoRechazo = null;
            String codigo = "00";
            if (!esBanco(a.bancoOrigen()) || !esBanco(a.bancoEmisor())) {
                codigo = "91";
                motivoRechazo = "Institución no disponible en el suiche";
            } else if (escenario == ESC_FONDOS || fondosInsuficientes(a)) {
                codigo = "51";
                motivoRechazo = "Fondos insuficientes";
            }
            if (motivoRechazo == null) {
                op.estado = AUTORIZADO;
                op.codigoAutorizacion = "AUT" + String.format("%06d",
                        Math.floorMod(Semilla.de("aut", a.referencia(), op.numero), 1_000_000L));
                op.respAutorizacion = respuesta(op, "aprobado", "00", null);
            } else {
                op.estado = RECHAZADO;
                op.respAutorizacion = respuesta(op, RECHAZADO, codigo, motivoRechazo);
            }
            operaciones.put(a.referencia(), op); // queda registrada ANTES del posible retardo
            respuesta = op.respAutorizacion;
            simularTimeout = escenario == ESC_TIMEOUT && op.estado.equals(AUTORIZADO);
        }
        if (simularTimeout) {
            dormir(retardoTimeoutMs);
        }
        return respuesta;
    }

    public synchronized RespuestaSuiche capturar(String referencia, String codigoAutorizacion) {
        OperacionSim op = obtener(referencia);
        if (op.respCaptura != null) {
            return op.respCaptura;
        }
        if (op.estado.equals(RECHAZADO)) {
            op.respCaptura = respuesta(op, RECHAZADO, "05", "La operación no fue autorizada");
        } else if (op.estado.equals(REVERSADO)) {
            op.respCaptura = respuesta(op, RECHAZADO, "05", "La operación fue reversada");
        } else if (codigoAutorizacion != null && !codigoAutorizacion.equals(op.codigoAutorizacion)) {
            // no se guarda: un reintento con el código correcto debe poder capturar
            return respuesta(op, RECHAZADO, "05", "El código de autorización no corresponde a la operación");
        } else if (op.escenario() == ESC_CAPTURA_RECHAZADA) {
            // la operación sigue "autorizado": el núcleo debe reversarla
            op.respCaptura = respuesta(op, RECHAZADO, "05", "Operación no permitida por el emisor");
        } else {
            banco.aplicarPagoTdc(op.refCuentaOrigen, op.refTarjeta, op.montoCts, op.referencia);
            op.estado = CAPTURADO;
            op.actualizada = tiempo.ahora();
            op.liquidable = op.actualizada;
            op.respCaptura = respuesta(op, "aprobado", "00", null);
        }
        return op.respCaptura;
    }

    public synchronized RespuestaSuiche reversar(String referencia, String motivo) {
        OperacionSim op = obtener(referencia);
        if (op.respReverso != null) {
            return op.respReverso;
        }
        switch (op.estado) {
            case CAPTURADO -> {
                banco.revertirPagoTdc(op.refCuentaOrigen, op.refTarjeta, op.montoCts, op.referencia);
                marcarReversado(op, motivo);
            }
            case AUTORIZADO -> {
                marcarReversado(op, motivo);
                op.liquidable = op.actualizada; // nunca se capturó: aparece el día del reverso
            }
            default -> op.respReverso = respuesta(op, RECHAZADO, "05", "La operación no admite reverso (" + op.estado + ")");
        }
        return op.respReverso;
    }

    public Operacion consultar(String referencia) {
        OperacionSim op = operaciones.get(referencia);
        if (op == null) {
            return new Operacion(referencia, NO_EXISTE, null, null, null, null);
        }
        synchronized (this) {
            return op.aOperacion();
        }
    }

    /** Archivo de liquidación: capturadas/reversadas del día (zona Caracas), salvo escenarios 98/99. */
    public synchronized ArchivoLiquidacion liquidacion(LocalDate fecha) {
        List<OperacionSim> delDia = operaciones.values().stream()
                .filter(op -> op.liquidable != null && op.liquidable.toLocalDate().equals(fecha))
                .filter(op -> op.estado.equals(CAPTURADO) || op.estado.equals(REVERSADO))
                .filter(op -> op.escenario() != ESC_FALTA_EN_LIQUIDACION)
                .sorted(Comparator.comparing((OperacionSim op) -> op.liquidable).thenComparing(op -> op.numero))
                .toList();
        List<RegistroLiquidacion> registros = new ArrayList<>();
        long total = 0;
        for (OperacionSim op : delDia) {
            long monto = op.escenario() == ESC_MONTO_DISTINTO ? op.montoCts + 100 : op.montoCts;
            registros.add(new RegistroLiquidacion(registros.size() + 1, op.referencia, op.referenciaSuiche, monto,
                    op.estado, Tiempo.formatear(op.liquidable)));
            total += monto;
        }
        String yyyymmdd = fecha.format(AAAAMMDD);
        return new ArchivoLiquidacion(fecha.toString(), "LIQ_S7B_" + yyyymmdd + ".json",
                Semilla.sha256Hex(jsonCanonico(registros)), registros.size(), total, registros);
    }

    /** Para la consola: últimas operaciones, la más reciente primero. */
    public synchronized List<Operacion> ultimas(int maximo) {
        return operaciones.values().stream()
                .sorted(Comparator.comparingLong((OperacionSim op) -> op.numero).reversed())
                .limit(maximo)
                .map(OperacionSim::aOperacion)
                .toList();
    }

    /**
     * JSON canónico de los registros (estilo RFC 8785): arreglo de objetos con las claves en orden
     * alfabético, sin espacios, UTF-8. {@code hashArchivo} = SHA-256 (hex minúsculas) de este texto.
     */
    static String jsonCanonico(List<RegistroLiquidacion> registros) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < registros.size(); i++) {
            RegistroLiquidacion r = registros.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"estado\":").append(cadena(r.estado()))
                    .append(",\"fechaHoraOperacion\":").append(cadena(r.fechaHoraOperacion()))
                    .append(",\"linea\":").append(r.linea())
                    .append(",\"montoCts\":").append(r.montoCts())
                    .append(",\"referencia\":").append(cadena(r.referencia()))
                    .append(",\"referenciaSuiche\":").append(cadena(r.referenciaSuiche()))
                    .append('}');
        }
        return sb.append(']').toString();
    }

    private static String cadena(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ---------------------------------------------------------------- auxiliares

    private void marcarReversado(OperacionSim op, String motivo) {
        op.estado = REVERSADO;
        op.actualizada = tiempo.ahora();
        op.motivoReverso = motivo;
        op.respReverso = respuesta(op, "aprobado", "00", null);
    }

    private boolean fondosInsuficientes(Autorizacion a) {
        return banco.buscarCuenta(a.refCuentaOrigen())
                .map(CuentaSim::saldoDisponibleCts)
                .map(disponible -> disponible < a.montoCts())
                .orElse(false);
    }

    private static boolean esBanco(String codigo) {
        return Institucion.buscar(codigo).map(Institucion::esBanco).orElse(false);
    }

    private OperacionSim obtener(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            throw ApiException.solicitudInvalida("Falta 'referencia'");
        }
        OperacionSim op = operaciones.get(referencia);
        if (op == null) {
            throw ApiException.noEncontrado("OPERACION_NO_EXISTE",
                    "No existe una operación con la referencia '" + referencia + "'");
        }
        return op;
    }

    private RespuestaSuiche respuesta(OperacionSim op, String resultado, String codigo, String motivo) {
        return new RespuestaSuiche(op.referencia, resultado, codigo, op.codigoAutorizacion, op.referenciaSuiche,
                motivo, Tiempo.formatear(tiempo.ahora()));
    }

    private static void validar(Autorizacion a) {
        if (a == null || a.referencia() == null || a.referencia().isBlank() || a.montoCts() == null
                || a.bancoOrigen() == null || a.bancoEmisor() == null || a.refTarjeta() == null) {
            throw ApiException.solicitudInvalida(
                    "Se requieren referencia, bancoOrigen, refCuentaOrigen, bancoEmisor, refTarjeta y montoCts");
        }
        if (a.montoCts() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MONTO_INVALIDO", "montoCts debe ser positivo");
        }
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Estado mutable de una operación (protegido por el monitor del servicio). */
    private static final class OperacionSim {
        final String referencia;
        final String refCuentaOrigen;
        final String refTarjeta;
        final long montoCts;
        final long numero;
        final OffsetDateTime creada;
        String referenciaSuiche;
        String codigoAutorizacion;
        String estado;
        String motivoReverso;
        OffsetDateTime actualizada;
        /** Momento que determina el día de liquidación (captura, o reverso si nunca se capturó). */
        OffsetDateTime liquidable;
        RespuestaSuiche respAutorizacion;
        RespuestaSuiche respCaptura;
        RespuestaSuiche respReverso;

        OperacionSim(Autorizacion a, long numero, OffsetDateTime creada) {
            this.referencia = a.referencia();
            this.refCuentaOrigen = a.refCuentaOrigen();
            this.refTarjeta = a.refTarjeta();
            this.montoCts = a.montoCts();
            this.numero = numero;
            this.creada = creada;
            this.actualizada = creada;
        }

        int escenario() {
            return (int) (montoCts % 100);
        }

        Operacion aOperacion() {
            return new Operacion(referencia, estado, referenciaSuiche, codigoAutorizacion, montoCts,
                    Tiempo.formatear(actualizada));
        }
    }
}
