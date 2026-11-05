package ve.impulsatech.ahorramas.cofre.dominio;

import java.time.OffsetDateTime;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;

/**
 * REGLA_AHORRO (supertipo) con subtipos PORCENTAJE_INGRESO, REDONDEO y APORTE_FIJO_QUINCENAL (R-13).
 * Solo se activa con aprobación por huella/PIN.
 */
public record ReglaAhorro(
        Long idRegla,
        long idMeta,
        Long idRecomendacion,
        String tipo,
        OffsetDateTime fechaAprobacion,
        OffsetDateTime fechaInicioVigencia,
        OffsetDateTime fechaFinVigencia,
        String estatus,
        Integer porcentajePb,
        Long multiploRedondeoCts,
        Long montoFijoUsdCts) {

    public static ReglaAhorro aprobar(long idMeta, String tipo, Integer porcentajePb, Long multiploRedondeoCts, Long montoFijoUsdCts,
                                      Long idRecomendacion, OffsetDateTime aprobadaEn) {
        if (tipo == null) {
            throw ErrorNegocio.validacion("Tipo de regla requerido");
        }
        switch (tipo) {
            case "pct_ingreso" -> {
                if (porcentajePb == null || porcentajePb < 1 || porcentajePb > 5_000) {
                    throw ErrorNegocio.validacion("El porcentaje debe estar entre 0,01 % y 50 % (porcentajePb 1–5000)");
                }
                return new ReglaAhorro(null, idMeta, idRecomendacion, tipo, aprobadaEn, aprobadaEn, null, "activa", porcentajePb, null, null);
            }
            case "redondeo" -> {
                if (multiploRedondeoCts == null || multiploRedondeoCts < 100) {
                    throw ErrorNegocio.validacion("El múltiplo de redondeo debe ser de al menos Bs 1,00 (100 céntimos)");
                }
                return new ReglaAhorro(null, idMeta, idRecomendacion, tipo, aprobadaEn, aprobadaEn, null, "activa", null, multiploRedondeoCts, null);
            }
            case "fijo_quincenal" -> {
                if (montoFijoUsdCts == null || montoFijoUsdCts <= 0) {
                    throw ErrorNegocio.validacion("El aporte fijo quincenal debe ser positivo (céntimos de USD)");
                }
                return new ReglaAhorro(null, idMeta, idRecomendacion, tipo, aprobadaEn, aprobadaEn, null, "activa", null, null, montoFijoUsdCts);
            }
            default -> throw ErrorNegocio.validacion("Tipo de regla inválido: " + tipo);
        }
    }

    public boolean activa() {
        return "activa".equals(estatus);
    }

    public ReglaAhorro conEstatus(String nuevo, OffsetDateTime ahora) {
        if ("finalizada".equals(estatus)) {
            throw ErrorNegocio.regla("REGLA_FINALIZADA", "La regla ya fue finalizada");
        }
        if (!nuevo.equals("activa") && !nuevo.equals("pausada") && !nuevo.equals("finalizada")) {
            throw ErrorNegocio.validacion("Estatus inválido");
        }
        return new ReglaAhorro(idRegla, idMeta, idRecomendacion, tipo, fechaAprobacion, fechaInicioVigencia,
                "finalizada".equals(nuevo) ? ahora : null, nuevo, porcentajePb, multiploRedondeoCts, montoFijoUsdCts);
    }

    /**
     * Aporte (USD céntimos) que la regla genera ante un movimiento bancario; 0 si no aplica.
     * <ul>
     *   <li>pct_ingreso: % de cada ingreso, convertido a USD a la tasa del día.</li>
     *   <li>redondeo: cada gasto en Bs se redondea al múltiplo y la diferencia se aparta.</li>
     * </ul>
     */
    public long aporteParaMovimiento(String clasificacion, long montoCts, Moneda moneda, TasaBcv tasa) {
        return switch (tipo) {
            case "pct_ingreso" -> "ingreso".equals(clasificacion) ? Montos.porcentajePb(tasa.aUsd(montoCts, moneda), porcentajePb) : 0;
            case "redondeo" -> {
                if (!"gasto".equals(clasificacion) || moneda != Moneda.VES) {
                    yield 0;
                }
                long redondeado = Montos.dividirHaciaArriba(montoCts, multiploRedondeoCts) * multiploRedondeoCts;
                long diferenciaBs = redondeado - montoCts;
                yield diferenciaBs == 0 ? 0 : Math.max(1, tasa.bsAUsd(diferenciaBs));
            }
            default -> 0;
        };
    }
}
