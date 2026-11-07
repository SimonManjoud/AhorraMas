package ve.impulsatech.ahorramas.simuladores.instituciones;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import ve.impulsatech.ahorramas.simuladores.comun.ApiException;
import ve.impulsatech.ahorramas.simuladores.comun.Semilla;
import ve.impulsatech.ahorramas.simuladores.comun.Tiempo;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Cuenta;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.EstadoCuenta;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.InyectarMovimiento;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Movimiento;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Saldo;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Tarjeta;

/**
 * "Core bancario" en memoria de todas las instituciones simuladas. Las cuentas, tarjetas y
 * movimientos se generan de forma determinista a partir de (institución, cédula); los saldos
 * mutan con los movimientos inyectados y con las capturas/reversos del suiche.
 */
@Service
public class BancoService {

    static final int DIAS_MOVIMIENTOS = 60;
    private static final Pattern CEDULA = Pattern.compile("[VE]\\d{6,9}");
    private static final Set<String> NATURALEZAS = Set.of("credito", "debito");
    private static final Set<String> CLASIFICACIONES = Set.of("ingreso", "gasto", "transf_propia", "pago_tdc");
    private static final String[] NOMBRES = {"MARIA PEREZ", "JOSE RODRIGUEZ", "ANA GONZALEZ", "CARLOS HERNANDEZ",
            "LUISA MARTINEZ", "PEDRO GARCIA", "CARMEN LOPEZ", "JESUS SANCHEZ", "GABRIELA RAMIREZ", "LUIS TORRES",
            "DANIELA FLORES", "MIGUEL DIAZ"};
    private static final String[] GASTOS_VES = {"PAGO MOVIL SUPERMERCADO", "PUNTO DE VENTA FARMACIA",
            "PUNTO DE VENTA PANADERIA", "PAGO SERVICIO INTERNET", "PAGO MOVIL TRANSPORTE", "PUNTO DE VENTA MERCADO",
            "PAGO SERVICIO ELECTRICIDAD", "COMPRA EN LINEA", "PAGO MOVIL COMIDA", "RECARGA TELEFONIA"};

    private final Tiempo tiempo;
    private final Map<String, Cliente> clientes = new ConcurrentHashMap<>();
    private final Map<String, CuentaSim> cuentasPorRef = new ConcurrentHashMap<>();
    private final Map<String, TarjetaSim> tarjetasPorRef = new ConcurrentHashMap<>();
    private final AtomicLong secuenciaMovimientos = new AtomicLong();

    public BancoService(Tiempo tiempo, @Value("${simulador.cedulas-demo:}") List<String> cedulasDemo) {
        this.tiempo = tiempo;
        for (String cedula : cedulasDemo) {
            if (!cedula.isBlank()) {
                for (Institucion inst : Institucion.CATALOGO) {
                    cliente(inst.codigo(), normalizarCedula(cedula));
                }
            }
        }
    }

    // ---------------------------------------------------------------- consultas Open Banking

    public List<Cuenta> cuentas(String codigo, String cedula) {
        return cliente(codigo, cedula).cuentas.stream().map(CuentaSim::aDto).toList();
    }

    public Saldo saldo(String codigo, String cedula, String refCuenta) {
        return cuentaDe(codigo, cedula, refCuenta).saldo(tiempo.ahora());
    }

    public List<Movimiento> movimientos(String codigo, String cedula, String refCuenta, LocalDate desde) {
        CuentaSim cuenta = cuentaDe(codigo, cedula, refCuenta);
        OffsetDateTime ahora = tiempo.ahora();
        LocalDate hoy = tiempo.hoy();
        LocalDate minimo = hoy.minusDays(DIAS_MOVIMIENTOS);
        LocalDate inicio = (desde == null || desde.isBefore(minimo)) ? minimo : desde;

        List<Mov> todos = new ArrayList<>(generados(cuenta, inicio, hoy));
        todos.addAll(cuenta.extras());
        return todos.stream()
                .filter(m -> !m.fechaHora.toLocalDate().isBefore(inicio) && !m.fechaHora.isAfter(ahora))
                .sorted(Comparator.comparing((Mov m) -> m.fechaHora).thenComparing(m -> m.ref))
                .map(Mov::aDto)
                .toList();
    }

    public List<Tarjeta> tarjetas(String codigo, String cedula) {
        return cliente(codigo, cedula).tarjetas.stream().map(TarjetaSim::aDto).toList();
    }

    // ---------------------------------------------------------------- uso interno (suiche, consola)

    public Optional<TarjetaSim> buscarTarjeta(String refTarjeta) {
        return Optional.ofNullable(refTarjeta).map(tarjetasPorRef::get);
    }

    public Optional<CuentaSim> buscarCuenta(String refCuenta) {
        return Optional.ofNullable(refCuenta).map(cuentasPorRef::get);
    }

    /** Captura del suiche: debita la cuenta origen y abona la TDC (si el simulador las conoce). */
    public void aplicarPagoTdc(String refCuenta, String refTarjeta, long montoCts, String referencia) {
        String ultimos4 = buscarTarjeta(refTarjeta).map(t -> t.ultimos4).orElse("????");
        buscarCuenta(refCuenta).ifPresent(c -> c.registrar(nuevoMovimiento(c, tiempo.ahora(), "debito", montoCts,
                "PAGO TDC *" + ultimos4 + " AHORRA+ REF " + corta(referencia), "pago_tdc")));
        buscarTarjeta(refTarjeta).ifPresent(t -> t.abonar(montoCts));
    }

    /** Reverso del suiche: deshace {@link #aplicarPagoTdc}. */
    public void revertirPagoTdc(String refCuenta, String refTarjeta, long montoCts, String referencia) {
        buscarCuenta(refCuenta).ifPresent(c -> c.registrar(nuevoMovimiento(c, tiempo.ahora(), "credito", montoCts,
                "REVERSO PAGO TDC AHORRA+ REF " + corta(referencia), "pago_tdc")));
        buscarTarjeta(refTarjeta).ifPresent(t -> t.abonar(-montoCts));
    }

    /** Control demo: inyecta un movimiento y ajusta el saldo. */
    public Movimiento inyectar(String codigo, InyectarMovimiento req) {
        if (req == null || req.cedula() == null || req.montoCts() == null) {
            throw ApiException.solicitudInvalida("Se requieren 'cedula' y 'montoCts'");
        }
        if (req.montoCts() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MONTO_INVALIDO", "montoCts debe ser positivo");
        }
        String naturaleza = req.naturaleza() == null ? "credito" : req.naturaleza().toLowerCase(Locale.ROOT);
        if (!NATURALEZAS.contains(naturaleza)) {
            throw ApiException.solicitudInvalida("naturaleza debe ser 'credito' o 'debito'");
        }
        String clasificacion = req.clasificacion() == null
                ? (naturaleza.equals("credito") ? "ingreso" : "gasto")
                : req.clasificacion().toLowerCase(Locale.ROOT);
        if (!CLASIFICACIONES.contains(clasificacion)) {
            throw ApiException.solicitudInvalida("clasificacion debe ser ingreso|gasto|transf_propia|pago_tdc");
        }
        String cedula = normalizarCedula(req.cedula());
        Cliente cliente = cliente(codigo, cedula);
        CuentaSim cuenta = (req.refCuenta() == null || req.refCuenta().isBlank())
                ? cliente.cuentas.getFirst()
                : cuentaDe(codigo, cedula, req.refCuenta());
        String descripcion = req.descripcion() == null || req.descripcion().isBlank()
                ? (naturaleza.equals("credito") ? "PAGO NOMINA" : "PAGO COMERCIO")
                : req.descripcion().toUpperCase(Locale.ROOT);
        Mov mov = nuevoMovimiento(cuenta, tiempo.ahora(), naturaleza, req.montoCts(), descripcion, clasificacion);
        cuenta.registrar(mov);
        return mov.aDto();
    }

    /** Resumen para la consola: cuentas generadas hasta ahora. */
    public List<CuentaSim> todasLasCuentas() {
        return cuentasPorRef.values().stream()
                .sorted(Comparator.comparing((CuentaSim c) -> c.cedula).thenComparing(c -> c.ref))
                .toList();
    }

    public static String normalizarCedula(String cedula) {
        String c = cedula == null ? "" : cedula.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s.\\-]", "");
        if (!c.isEmpty() && Character.isDigit(c.charAt(0))) {
            c = "V" + c;
        }
        if (!CEDULA.matcher(c).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CEDULA_INVALIDA",
                    "La cédula debe tener el formato V12345678 o E12345678");
        }
        return c;
    }

    // ---------------------------------------------------------------- generación determinista

    private Cliente cliente(String codigo, String cedula) {
        Institucion inst = Institucion.obtener(codigo);
        return clientes.computeIfAbsent(codigo + "|" + cedula, k -> generarCliente(inst, cedula));
    }

    private CuentaSim cuentaDe(String codigo, String cedula, String refCuenta) {
        return cliente(codigo, cedula).cuentas.stream()
                .filter(c -> c.ref.equals(refCuenta))
                .findFirst()
                .orElseThrow(() -> ApiException.noEncontrado("CUENTA_NO_EXISTE",
                        "La cuenta '" + refCuenta + "' no existe o no pertenece al titular"));
    }

    private Cliente generarCliente(Institucion inst, String cedula) {
        String titular = NOMBRES[(int) Math.floorMod(Semilla.de("titular", cedula), (long) NOMBRES.length)];
        Random r = Semilla.random("cliente", inst.codigo(), cedula);
        List<CuentaSim> cuentas = new ArrayList<>();
        List<TarjetaSim> tarjetas = new ArrayList<>();
        if (inst.esBanco()) {
            boolean corriente = r.nextBoolean();
            cuentas.add(new CuentaSim(ref(inst, cedula, corriente ? "CC" : "AH"), inst.codigo(), cedula,
                    cuatroDigitos(r), corriente ? "corriente" : "ahorro", "VES", titular,
                    2_000_000 + r.nextLong(13_000_000), r.nextInt(4) == 0 ? r.nextLong(1, 60_000) : 0));
            if (r.nextBoolean()) {
                cuentas.add(new CuentaSim(ref(inst, cedula, "DV"), inst.codigo(), cedula, cuatroDigitos(r),
                        "divisas", "USD", titular, 5_000 + r.nextLong(75_000), 0));
            }
            if (inst.emiteTdc()) {
                LocalDate hoy = tiempo.hoy();
                LocalDate corte = hoy.getDayOfMonth() >= 28 ? hoy.withDayOfMonth(28)
                        : hoy.minusMonths(1).withDayOfMonth(28);
                long saldoTotal = 5_000_000 + r.nextLong(20_000_000);
                tarjetas.add(new TarjetaSim("TDC-" + inst.codigo() + "-" + Semilla.hex(4, "tdc", inst.codigo(), cedula),
                        inst.codigo(), cedula, cuatroDigitos(r), titular, corte, corte.plusDays(15),
                        saldoTotal, saldoTotal / 10, inst.codigo().equals("0902") ? "mastercard" : "visa"));
            }
        } else {
            cuentas.add(new CuentaSim(ref(inst, cedula, "BI"), inst.codigo(), cedula, cuatroDigitos(r),
                    "billetera", "USDT", titular, 2_000 + r.nextLong(48_000), 0));
        }
        cuentas.forEach(c -> cuentasPorRef.put(c.ref, c));
        tarjetas.forEach(t -> tarjetasPorRef.put(t.ref, t));
        return new Cliente(List.copyOf(cuentas), List.copyOf(tarjetas));
    }

    private static String ref(Institucion inst, String cedula, String sufijo) {
        return inst.codigo() + "-" + sufijo + "-" + Semilla.hex(6, "cuenta", inst.codigo(), cedula, sufijo);
    }

    private static String cuatroDigitos(Random r) {
        return String.format("%04d", r.nextInt(10_000));
    }

    /** Movimientos generados mes a mes (cada mes con su propia semilla ⇒ estables en el tiempo). */
    private static List<Mov> generados(CuentaSim c, LocalDate desde, LocalDate hasta) {
        List<Mov> lista = new ArrayList<>();
        for (YearMonth ym = YearMonth.from(desde); !ym.isAfter(YearMonth.from(hasta)); ym = ym.plusMonths(1)) {
            new GeneradorMes(c, ym, lista).generar();
        }
        return lista;
    }

    /** Genera los movimientos de un mes de una cuenta con una semilla propia de (cuenta, mes). */
    private static final class GeneradorMes {
        private final CuentaSim c;
        private final YearMonth ym;
        private final List<Mov> destino;
        private final Random r;
        private int indice;

        GeneradorMes(CuentaSim c, YearMonth ym, List<Mov> destino) {
            this.c = c;
            this.ym = ym;
            this.destino = destino;
            this.r = Semilla.random("movimientos", c.ref, ym);
        }

        void generar() {
            switch (c.tipo) {
                case "corriente", "ahorro" -> {
                    long nomina = 1_500_000 + Math.floorMod(Semilla.de("nomina", c.ref), 4_500_000L);
                    agregar(ym.atDay(15), 6, 30, "credito", nomina, "PAGO NOMINA", "ingreso");
                    agregar(ym.atEndOfMonth(), 6, 30, "credito", nomina, "PAGO NOMINA", "ingreso");
                    int gastos = 6 + r.nextInt(7);
                    for (int g = 0; g < gastos; g++) {
                        agregar(diaAlAzar(), 8 + r.nextInt(12), r.nextInt(60), "debito",
                                30_000 + r.nextLong(770_000), GASTOS_VES[r.nextInt(GASTOS_VES.length)], "gasto");
                    }
                    if (r.nextBoolean()) {
                        agregar(ym.atDay(16 + r.nextInt(5)), 10, 0, "debito", 200_000 + r.nextLong(800_000),
                                "TRANSFERENCIA A CUENTA PROPIA", "transf_propia");
                    }
                }
                case "divisas" -> {
                    agregar(ym.atDay(1 + r.nextInt(10)), 11, r.nextInt(60), "credito", 2_000 + r.nextLong(18_000),
                            "DEPOSITO EN DIVISAS", "ingreso");
                    int gastos = 1 + r.nextInt(2);
                    for (int g = 0; g < gastos; g++) {
                        agregar(diaAlAzar(), 9 + r.nextInt(10), r.nextInt(60), "debito", 500 + r.nextLong(4_500),
                                "COMPRA CON TARJETA DIVISAS", "gasto");
                    }
                }
                default -> { // billetera USDT
                    agregar(diaAlAzar(), 12, r.nextInt(60), "credito", 1_000 + r.nextLong(9_000),
                            "COMPRA USDT P2P", "transf_propia");
                    int gastos = 1 + r.nextInt(3);
                    for (int g = 0; g < gastos; g++) {
                        agregar(diaAlAzar(), 9 + r.nextInt(12), r.nextInt(60), "debito", 300 + r.nextLong(2_700),
                                "PAGO COMERCIO USDT", "gasto");
                    }
                }
            }
        }

        private LocalDate diaAlAzar() {
            return ym.atDay(1 + r.nextInt(ym.lengthOfMonth()));
        }

        private void agregar(LocalDate fecha, int hora, int minuto, String naturaleza, long montoCts,
                             String descripcion, String clasificacion) {
            String ref = "MV-" + c.codigo + "-"
                    + String.format("%06d", Math.floorMod(Semilla.de("mv", c.ref, ym, indice++), 1_000_000L));
            destino.add(new Mov(ref, Tiempo.en(fecha, hora, minuto), naturaleza, montoCts, descripcion, clasificacion));
        }
    }

    private Mov nuevoMovimiento(CuentaSim c, OffsetDateTime fechaHora, String naturaleza, long montoCts,
                                String descripcion, String clasificacion) {
        String ref = "MV-" + c.codigo + "-S" + String.format("%05d", secuenciaMovimientos.incrementAndGet());
        return new Mov(ref, fechaHora, naturaleza, montoCts, descripcion, clasificacion);
    }

    private static String corta(String referencia) {
        return referencia == null ? "" : referencia.length() <= 12 ? referencia : referencia.substring(0, 12);
    }

    // ---------------------------------------------------------------- estado en memoria

    private record Cliente(List<CuentaSim> cuentas, List<TarjetaSim> tarjetas) {
    }

    private record Mov(String ref, OffsetDateTime fechaHora, String naturaleza, long montoCts, String descripcion,
                       String clasificacion) {
        Movimiento aDto() {
            return new Movimiento(ref, Tiempo.formatear(fechaHora), naturaleza, montoCts, descripcion, clasificacion);
        }
    }

    /** Cuenta simulada con saldo mutable. */
    public static final class CuentaSim {
        final String ref;
        final String codigo;
        final String cedula;
        final String ultimos4;
        final String tipo;
        final String moneda;
        final String titular;
        private long saldoCts;
        private final long retenidoCts;
        private final List<Mov> extras = new ArrayList<>();

        CuentaSim(String ref, String codigo, String cedula, String ultimos4, String tipo, String moneda,
                  String titular, long saldoCts, long retenidoCts) {
            this.ref = ref;
            this.codigo = codigo;
            this.cedula = cedula;
            this.ultimos4 = ultimos4;
            this.tipo = tipo;
            this.moneda = moneda;
            this.titular = titular;
            this.saldoCts = saldoCts;
            this.retenidoCts = retenidoCts;
        }

        public String ref() {
            return ref;
        }

        public String cedula() {
            return cedula;
        }

        public String codigo() {
            return codigo;
        }

        public String tipo() {
            return tipo;
        }

        public String moneda() {
            return moneda;
        }

        public synchronized long saldoCts() {
            return saldoCts;
        }

        public synchronized long saldoDisponibleCts() {
            return saldoCts - retenidoCts;
        }

        synchronized void registrar(Mov mov) {
            saldoCts += mov.naturaleza.equals("credito") ? mov.montoCts : -mov.montoCts;
            extras.add(mov);
        }

        synchronized List<Mov> extras() {
            return List.copyOf(extras);
        }

        synchronized Saldo saldo(OffsetDateTime ahora) {
            return new Saldo(ref, moneda, saldoCts, saldoCts - retenidoCts, Tiempo.formatear(ahora));
        }

        Cuenta aDto() {
            return new Cuenta(ref, ultimos4, tipo, moneda, titular);
        }
    }

    /** Tarjeta de crédito simulada; los pagos reducen {@code saldoTotalCts}. */
    public static final class TarjetaSim {
        final String ref;
        final String codigo;
        final String cedula;
        final String ultimos4;
        final String titular;
        final String marca;
        final LocalDate fechaCorte;
        final LocalDate fechaLimitePago;
        private long saldoTotalCts;
        private final long pagoMinimoOriginalCts;
        private long pagadoCts;

        TarjetaSim(String ref, String codigo, String cedula, String ultimos4, String titular, LocalDate fechaCorte,
                   LocalDate fechaLimitePago, long saldoTotalCts, long pagoMinimoCts, String marca) {
            this.marca = marca;
            this.ref = ref;
            this.codigo = codigo;
            this.cedula = cedula;
            this.ultimos4 = ultimos4;
            this.titular = titular;
            this.fechaCorte = fechaCorte;
            this.fechaLimitePago = fechaLimitePago;
            this.saldoTotalCts = saldoTotalCts;
            this.pagoMinimoOriginalCts = pagoMinimoCts;
        }

        public String ref() {
            return ref;
        }

        public String codigo() {
            return codigo;
        }

        public String ultimos4() {
            return ultimos4;
        }

        public String titular() {
            return titular;
        }

        synchronized void abonar(long montoCts) {
            saldoTotalCts -= montoCts;
            pagadoCts += montoCts;
        }

        synchronized Tarjeta aDto() {
            long minimo = Math.max(0, Math.min(pagoMinimoOriginalCts - pagadoCts, saldoTotalCts));
            return new Tarjeta(ref, ultimos4, titular, new EstadoCuenta(fechaCorte.toString(),
                    fechaLimitePago.toString(), saldoTotalCts, minimo), marca);
        }
    }
}
