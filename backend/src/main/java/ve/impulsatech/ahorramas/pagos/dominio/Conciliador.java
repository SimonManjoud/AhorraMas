package ve.impulsatech.ahorramas.pagos.dominio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conciliación diaria (R-19): cruza cada pago con el archivo de liquidación del suiche.
 * Servicio de dominio puro: no conoce la base de datos ni el suiche.
 *
 * <ul>
 *   <li>Pago CAPTURADO + registro capturado con igual monto → {@code conciliado}.</li>
 *   <li>Pago CAPTURADO + monto distinto o registro reversado → {@code dif_monto}.</li>
 *   <li>Pago CAPTURADO sin registro → {@code falta_suiche}.</li>
 *   <li>Pago RECHAZADO/REVERSADO: concilia si el suiche no lo liquidó como capturado.</li>
 *   <li>Registro sin pago en Ahorra+ → {@code falta_ahorra}.</li>
 * </ul>
 */
public final class Conciliador {

    private Conciliador() {
    }

    public record PagoAConciliar(long idPago, String claveIdempotencia, String referenciaSuiche, long montoCts, EstadoPago estado) {
    }

    public record RegistroLiquidacion(int linea, String referenciaSuiche, String referencia, long montoCts, String estadoSuiche) {
    }

    public enum Tipo { conciliado, dif_monto, falta_suiche, falta_ahorra }

    public record Resultado(Long idPago, Integer linea, Tipo tipo, Long diferenciaCts) {
        public boolean diferencia() {
            return tipo != Tipo.conciliado;
        }
    }

    public static List<Resultado> conciliar(List<PagoAConciliar> pagos, List<RegistroLiquidacion> registros) {
        Map<String, RegistroLiquidacion> porRefSuiche = new HashMap<>();
        Map<String, RegistroLiquidacion> porReferencia = new HashMap<>();
        for (RegistroLiquidacion r : registros) {
            porRefSuiche.put(r.referenciaSuiche(), r);
            if (r.referencia() != null) {
                porReferencia.put(r.referencia(), r);
            }
        }
        Set<Integer> usados = new HashSet<>();
        List<Resultado> res = new ArrayList<>();
        for (PagoAConciliar p : pagos) {
            RegistroLiquidacion r = p.referenciaSuiche() == null ? null : porRefSuiche.get(p.referenciaSuiche());
            if (r == null) {
                r = porReferencia.get(p.claveIdempotencia());
            }
            if (r != null) {
                usados.add(r.linea());
            }
            Integer linea = r == null ? null : r.linea();
            boolean sinDinero = p.estado() == EstadoPago.REVERSADO || p.estado() == EstadoPago.RECHAZADO;
            if (sinDinero) {
                if (r != null && "capturado".equals(r.estadoSuiche())) {
                    res.add(new Resultado(p.idPago(), linea, Tipo.dif_monto, r.montoCts()));
                } else {
                    res.add(new Resultado(p.idPago(), linea, Tipo.conciliado, null));
                }
            } else if (r == null) {
                res.add(new Resultado(p.idPago(), null, Tipo.falta_suiche, -p.montoCts()));
            } else if (!"capturado".equals(r.estadoSuiche())) {
                res.add(new Resultado(p.idPago(), linea, Tipo.dif_monto, -p.montoCts()));
            } else if (r.montoCts() != p.montoCts()) {
                res.add(new Resultado(p.idPago(), linea, Tipo.dif_monto, r.montoCts() - p.montoCts()));
            } else {
                res.add(new Resultado(p.idPago(), linea, Tipo.conciliado, null));
            }
        }
        for (RegistroLiquidacion r : registros) {
            if (!usados.contains(r.linea())) {
                res.add(new Resultado(null, r.linea(), Tipo.falta_ahorra, r.montoCts()));
            }
        }
        return res;
    }
}
