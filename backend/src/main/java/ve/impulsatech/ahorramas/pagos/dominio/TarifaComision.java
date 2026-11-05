package ve.impulsatech.ahorramas.pagos.dominio;

import ve.impulsatech.ahorramas.compartido.dominio.Montos;

/** TARIFA_COMISION vigente: monto fijo + porcentaje en puntos básicos (R-17). */
public record TarifaComision(long idTarifa, long montoFijoCts, Integer porcentajePb) {

    public long calcular(long montoCts) {
        return montoFijoCts + (porcentajePb == null ? 0 : Montos.porcentajePb(montoCts, porcentajePb));
    }
}
