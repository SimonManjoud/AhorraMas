package ve.impulsatech.ahorramas.consolidacion.dominio;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;

/**
 * Posición global consolidada: suma de todas las cuentas activas expresada en Bs y en USD a la
 * tasa BCV del día (servicio de dominio puro).
 */
public record PosicionGlobal(TasaBcv tasa, long totalBsCts, long totalUsdCts, OffsetDateTime actualizadoEn, List<PorInstitucion> instituciones) {

    public record PorInstitucion(long idInstitucion, String nombre, String tipo, long totalBsCts, long totalUsdCts, List<Linea> cuentas) {
    }

    public record Linea(CuentaConSaldo cuenta, long saldoBsCts, long saldoUsdCts) {
    }

    public static PosicionGlobal calcular(List<CuentaConSaldo> cuentas, TasaBcv tasa) {
        Map<Long, List<Linea>> porInst = new LinkedHashMap<>();
        long totalBs = 0;
        long totalUsd = 0;
        OffsetDateTime ultima = null;
        List<CuentaConSaldo> ordenadas = new ArrayList<>(cuentas);
        ordenadas.sort(Comparator.comparing(CuentaConSaldo::nombreInstitucion).thenComparing(CuentaConSaldo::idCuenta));
        for (CuentaConSaldo c : ordenadas) {
            if (!c.activa()) {
                continue;
            }
            long bs = tasa.aBs(c.saldoOCero(), c.moneda());
            long usd = tasa.aUsd(c.saldoOCero(), c.moneda());
            totalBs += bs;
            totalUsd += usd;
            if (c.fechaSaldo() != null && (ultima == null || c.fechaSaldo().isAfter(ultima))) {
                ultima = c.fechaSaldo();
            }
            porInst.computeIfAbsent(c.idInstitucion(), k -> new ArrayList<>()).add(new Linea(c, bs, usd));
        }
        List<PorInstitucion> lista = new ArrayList<>();
        porInst.forEach((id, lineas) -> {
            CuentaConSaldo primera = lineas.getFirst().cuenta();
            lista.add(new PorInstitucion(id, primera.nombreInstitucion(), primera.tipoInstitucion(),
                    lineas.stream().mapToLong(Linea::saldoBsCts).sum(), lineas.stream().mapToLong(Linea::saldoUsdCts).sum(), lineas));
        });
        return new PosicionGlobal(tasa, totalBs, totalUsd, ultima, lista);
    }
}
