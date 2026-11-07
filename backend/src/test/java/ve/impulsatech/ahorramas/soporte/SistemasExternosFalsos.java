package ve.impulsatech.ahorramas.soporte;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.CuentaExterna;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.MovimientoExterno;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.SaldoExterno;
import ve.impulsatech.ahorramas.identidad.aplicacion.puerto.salida.ServiciosExternosIdentidad;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.puerto.PuertosNotificaciones;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.*;
import ve.impulsatech.ahorramas.perfilia.aplicacion.puerto.salida.PuertosPerfil;

/**
 * Dobles en proceso de los sistemas externos para las pruebas de integración (mismos escenarios
 * deterministas que mocks/simuladores). Reemplazan a los adaptadores HTTP vía {@code @Primary}:
 * es exactamente lo que permite la arquitectura hexagonal.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SistemasExternosFalsos {

    static final ZoneId VET = ZoneId.of("America/Caracas");

    @Bean @Primary
    BcvFalso bcvFalso() {
        return new BcvFalso();
    }

    @Bean @Primary
    OpenBankingFalso openBankingFalso() {
        return new OpenBankingFalso();
    }

    @Bean @Primary
    SuicheFalso suicheFalso() {
        return new SuicheFalso();
    }

    @Bean @Primary
    PuertosPagos.EmisorTarjetas emisorFalso() {
        return (codigo, token) -> codigo.equals("0902")
                ? List.of(new TarjetaExterna("TDC-0902-" + token.substring(token.length() - 6), "7781", "MARIA PEREZ",
                new EstadoCuentaExterno(LocalDate.now(VET).minusDays(7), LocalDate.now(VET).plusDays(8), 18_450_000, 1_845_000), "visa"))
                : List.of();
    }

    @Bean @Primary
    ServiciosExternosIdentidad.ProveedorKyc kycFalso() {
        return s -> String.valueOf(s.cedula()).endsWith("666")
                ? new ServiciosExternosIdentidad.ResultadoKyc("KYC-F", "rechazado", true, true, "Coincidencia en listas de sanciones")
                : new ServiciosExternosIdentidad.ResultadoKyc("KYC-" + s.cedula(), "aprobado", true, false, null);
    }

    @Bean @Primary
    ServiciosExternosIdentidad.EnvioSms smsFalso() {
        return (tel, titulo, msg) -> { };
    }

    @Bean @Primary
    PuertosPerfil.RedactorExplicaciones llmFalso() {
        return (seudonimo, c, r) -> Optional.of("Explicación simulada: perfil " + c.perfil() + " para el seudónimo " + seudonimo);
    }

    @Bean @Primary
    PuertosNotificaciones.CanalNotificaciones canalFalso() {
        return (canal, destino, titulo, mensaje, tipo) -> true;
    }

    // ------------------------------------------------------------------------------------------ BCV

    public static class BcvFalso implements PuertosConsolidacion.Bcv {
        /** 400 Bs/USD el 1-ene-2026 y +0,2 %/día (determinista). */
        public static long valor(LocalDate f) {
            long dias = f.toEpochDay() - LocalDate.of(2026, 1, 1).toEpochDay();
            return Math.round(4_000_000 * Math.pow(1.002, dias));
        }

        @Override
        public TasaBcv tasaHoy() {
            LocalDate hoy = LocalDate.now(VET);
            return new TasaBcv(hoy, valor(hoy));
        }

        @Override
        public List<TasaBcv> tasas(LocalDate desde, LocalDate hasta) {
            List<TasaBcv> l = new ArrayList<>();
            for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
                l.add(new TasaBcv(d, valor(d)));
            }
            return l;
        }

        @Override
        public Optional<Integer> inflacionPb(int anio, int mes) {
            return Optional.of(1_100);
        }
    }

    // ------------------------------------------------------------------------------------------ Open Banking

    public static class OpenBankingFalso implements PuertosConsolidacion.OpenBanking {
        public final Map<String, List<MovimientoExterno>> movimientos = new ConcurrentHashMap<>();

        @Override
        public String urlAutorizacion(String codigo, String estado, List<String> alcances, String loginHint) {
            if (codigo.startsWith("C") && alcances.contains("debito:tdc")) {
                throw ErrorNegocio.validacion("ALCANCE_NO_PERMITIDO");
            }
            return "http://simulador/ob/" + codigo + "/oauth/authorize?state=" + estado + "&login_hint=" + loginHint;
        }

        @Override
        public String canjearCodigo(String codigo, String code) {
            return "tok-" + codigo + "-" + UUID.randomUUID();
        }

        @Override
        public List<CuentaExterna> cuentas(String codigo, String token) {
            String sufijo = token.substring(token.length() - 6);
            if (codigo.startsWith("C")) {
                return List.of(new CuentaExterna(codigo + "-BW-" + sufijo, "0042", "billetera", "USDT", "MARIA PEREZ"));
            }
            List<CuentaExterna> l = new ArrayList<>(List.of(new CuentaExterna(codigo + "-CC-" + sufijo, "4821", "corriente", "VES", "MARIA PEREZ")));
            if (codigo.equals("0901")) {
                l.add(new CuentaExterna(codigo + "-DV-" + sufijo, "9910", "divisas", "USD", "MARIA PEREZ"));
            }
            return l;
        }

        @Override
        public SaldoExterno saldo(String codigo, String token, String refCuenta) {
            long saldo = refCuenta.contains("-DV-") ? 50_000 : refCuenta.contains("-BW-") ? 25_000 : 100_000_000;
            return new SaldoExterno(refCuenta, "VES", saldo, saldo, OffsetDateTime.now(VET));
        }

        @Override
        public List<MovimientoExterno> movimientos(String codigo, String token, String refCuenta, LocalDate desde) {
            if (!refCuenta.contains("-CC-")) {
                return List.of();
            }
            List<MovimientoExterno> pendientes = movimientos.remove(codigo);
            return pendientes == null ? List.of() : pendientes;
        }

        /** Inyecta movimientos que recibirá la próxima sincronización de la cuenta corriente de ese banco. */
        public void inyectar(String codigo, MovimientoExterno m) {
            movimientos.computeIfAbsent(codigo, k -> new CopyOnWriteArrayList<>()).add(m);
        }

        @Override
        public void revocar(String codigo, String token) {
        }
    }

    // ------------------------------------------------------------------------------------------ Suiche

    /** Suiche idempotente por referencia con los escenarios del contrato (13, 51, 77, 98, 99). */
    public static class SuicheFalso implements PuertosPagos.Suiche {
        public record Op(String referencia, long monto, String estado, String refSuiche, String codAut, LocalDate fecha) {
        }

        public final Map<String, Op> ops = new ConcurrentHashMap<>();
        public final Map<String, AtomicInteger> capturasPorReferencia = new ConcurrentHashMap<>();
        private final AtomicInteger secuencia = new AtomicInteger();

        @Override
        public Beneficiario consultarBeneficiario(String bancoEmisor, String refTarjeta) {
            return new Beneficiario("MARIA PEREZ", "7781", refTarjeta);
        }

        @Override
        public synchronized RespuestaSuiche autorizar(SolicitudAutorizacion s) {
            Op existente = ops.get(s.referencia());
            if (existente != null) {
                return respuesta(existente, !"rechazado".equals(existente.estado()));
            }
            if (s.montoCts() % 100 == 51) {
                ops.put(s.referencia(), new Op(s.referencia(), s.montoCts(), "rechazado", null, null, LocalDate.now(VET)));
                return new RespuestaSuiche(s.referencia(), "rechazado", "51", null, null, "Fondos insuficientes");
            }
            int n = secuencia.incrementAndGet();
            Op op = new Op(s.referencia(), s.montoCts(), "autorizado", "S7B-TEST-" + n, "AUT" + n, LocalDate.now(VET));
            ops.put(s.referencia(), op);
            if (s.montoCts() % 100 == 13) {
                throw new SuicheSinRespuesta("timeout simulado", null);   // procesada, pero la respuesta "se pierde"
            }
            return respuesta(op, true);
        }

        @Override
        public synchronized RespuestaSuiche capturar(String referencia, String codigoAutorizacion) {
            Op op = ops.get(referencia);
            if (op == null) {
                return new RespuestaSuiche(referencia, "rechazado", "404", null, null, "Operación no existe");
            }
            if ("capturado".equals(op.estado())) {
                return respuesta(op, true);   // idempotente: nunca se debita dos veces
            }
            if (op.monto() % 100 == 77) {
                return new RespuestaSuiche(referencia, "rechazado", "05", null, op.refSuiche(), "Operación no permitida por el emisor");
            }
            capturasPorReferencia.computeIfAbsent(referencia, k -> new AtomicInteger()).incrementAndGet();
            Op cap = new Op(referencia, op.monto(), "capturado", op.refSuiche(), op.codAut(), LocalDate.now(VET));
            ops.put(referencia, cap);
            return respuesta(cap, true);
        }

        @Override
        public synchronized RespuestaSuiche reversar(String referencia, String motivo) {
            Op op = ops.get(referencia);
            if (op == null) {
                return new RespuestaSuiche(referencia, "rechazado", "404", null, null, "Operación no existe");
            }
            Op rev = new Op(referencia, op.monto(), "reversado", op.refSuiche(), op.codAut(), LocalDate.now(VET));
            ops.put(referencia, rev);
            return respuesta(rev, true);
        }

        @Override
        public OperacionSuiche consultar(String referencia) {
            Op op = ops.get(referencia);
            return op == null ? new OperacionSuiche(referencia, "no_existe", null, null, null)
                    : new OperacionSuiche(referencia, op.estado(), op.refSuiche(), op.codAut(), op.monto());
        }

        @Override
        public ArchivoLiquidacion liquidacion(LocalDate fecha) {
            List<RegistroArchivo> regs = new ArrayList<>();
            int linea = 1;
            for (Op op : ops.values().stream().sorted(java.util.Comparator.comparing(Op::referencia)).toList()) {
                boolean liquidable = "capturado".equals(op.estado()) || "reversado".equals(op.estado());
                if (!liquidable || !op.fecha().equals(fecha) || op.monto() % 100 == 98 && "capturado".equals(op.estado())) {
                    continue;
                }
                long monto = op.monto() % 100 == 99 && "capturado".equals(op.estado()) ? op.monto() + 100 : op.monto();
                regs.add(new RegistroArchivo(linea++, op.referencia(), op.refSuiche() == null ? "S7B-NA-" + linea : op.refSuiche(), monto,
                        op.estado(), OffsetDateTime.now(VET)));
            }
            return new ArchivoLiquidacion(fecha, "LIQ_TEST_" + fecha + ".json", "0".repeat(64), true, regs.size(),
                    regs.stream().mapToLong(RegistroArchivo::montoCts).sum(), regs);
        }

        private static RespuestaSuiche respuesta(Op op, boolean aprobado) {
            return new RespuestaSuiche(op.referencia(), aprobado ? "aprobado" : "rechazado", aprobado ? "00" : "51", op.codAut(), op.refSuiche(),
                    aprobado ? null : "Fondos insuficientes");
        }
    }
}
