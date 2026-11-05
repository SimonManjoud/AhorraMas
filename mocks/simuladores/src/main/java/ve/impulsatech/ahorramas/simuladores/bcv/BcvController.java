package ve.impulsatech.ahorramas.simuladores.bcv;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ve.impulsatech.ahorramas.simuladores.bcv.BcvDtos.FijarTasa;
import ve.impulsatech.ahorramas.simuladores.bcv.BcvDtos.Inflacion;
import ve.impulsatech.ahorramas.simuladores.bcv.BcvDtos.Tasa;
import ve.impulsatech.ahorramas.simuladores.comun.ApiException;

/** Simulador del BCV: tasa oficial e inflación (contrato §1). */
@RestController
public class BcvController {

    private final BcvService bcv;

    public BcvController(BcvService bcv) {
        this.bcv = bcv;
    }

    @GetMapping("/bcv/tasas/hoy")
    public Tasa tasaHoy(@RequestParam(defaultValue = "USD") String moneda) {
        return bcv.tasaHoy(moneda);
    }

    @GetMapping("/bcv/tasas")
    public List<Tasa> tasas(@RequestParam(defaultValue = "USD") String moneda,
                            @RequestParam String desde,
                            @RequestParam(required = false) String hasta) {
        return bcv.serie(moneda, fecha(desde, "desde"), hasta == null ? null : fecha(hasta, "hasta"));
    }

    /** {@code ?anio=2026&mes=10} → un mes; {@code ?desde=2026-01[&hasta=2026-09]} → lista. */
    @GetMapping("/bcv/inflacion")
    public Object inflacion(@RequestParam(required = false) Integer anio,
                            @RequestParam(required = false) Integer mes,
                            @RequestParam(required = false) String desde,
                            @RequestParam(required = false) String hasta) {
        if (anio != null && mes != null) {
            return bcv.inflacion(anio, mes);
        }
        if (desde != null) {
            List<Inflacion> lista = bcv.inflacionDesde(mesAnio(desde, "desde"),
                    hasta == null ? null : mesAnio(hasta, "hasta"));
            return lista;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "PARAMETRO_FALTANTE",
                "Indique 'anio' y 'mes', o 'desde' (yyyy-MM)");
    }

    @PostMapping("/simulador/bcv/tasa-hoy")
    public Tasa fijarTasa(@RequestBody FijarTasa cuerpo) {
        return bcv.fijarTasaHoy(cuerpo.valorBsX10000());
    }

    /** Extra (no está en el contrato): vuelve a la tasa generada. */
    @DeleteMapping("/simulador/bcv/tasa-hoy")
    public Tasa quitarTasa() {
        return bcv.quitarTasaForzada();
    }

    private static LocalDate fecha(String valor, String nombre) {
        try {
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FECHA_INVALIDA", "'" + nombre + "' debe ser yyyy-MM-dd");
        }
    }

    private static YearMonth mesAnio(String valor, String nombre) {
        try {
            return YearMonth.parse(valor);
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FECHA_INVALIDA", "'" + nombre + "' debe ser yyyy-MM");
        }
    }
}
