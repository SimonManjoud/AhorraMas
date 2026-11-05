package ve.impulsatech.ahorramas.consolidacion.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import ve.impulsatech.ahorramas.compartido.dominio.TasaBcv;

/** Tasa oficial BCV fechada (R-07) e inflación mensual publicada. */
public interface TasasApi {

    /** Tasa BCV vigente el día indicado (si ese día no hubo publicación, la última anterior). */
    TasaBcv tasaDe(LocalDate fecha);

    TasaBcv tasaHoy();

    List<TasaBcv> serie(LocalDate desde, LocalDate hasta);

    /** Variación mensual publicada, en puntos básicos; vacío si el mes aún no se publica. */
    Optional<Integer> inflacionMensualPb(int anio, int mes);
}
