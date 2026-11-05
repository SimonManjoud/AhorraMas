package ve.impulsatech.ahorramas.demo;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import ve.impulsatech.ahorramas.cofre.api.CofreApi;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.compartido.infraestructura.RelojAjustable;
import ve.impulsatech.ahorramas.pagos.api.PagosApi;

/** Modo demo: avanza días y ejecuta el cierre (racha/colchón, cuotas, reglas quincenales, conciliación). */
@RestController
@RequestMapping("/api/v1/demo")
@ConditionalOnProperty(name = "ahorra.demo.habilitado", havingValue = "true")
class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final RelojAjustable reloj;
    private final CofreApi cofre;
    private final PagosApi pagos;

    DemoController(RelojAjustable reloj, CofreApi cofre, PagosApi pagos) {
        this.reloj = reloj;
        this.cofre = cofre;
        this.pagos = pagos;
    }

    record AvanceDto(Integer dias) {
    }

    @GetMapping("/reloj")
    Map<String, Object> reloj() {
        return Map.of("fechaActual", reloj.hoy(), "ahora", reloj.ahora());
    }

    @PostMapping("/reloj/avanzar")
    Map<String, Object> avanzar(@RequestBody(required = false) AvanceDto d) {
        int dias = d == null || d.dias() == null ? 1 : d.dias();
        if (dias < 1 || dias > 31) {
            throw ErrorNegocio.validacion("Se puede avanzar entre 1 y 31 días");
        }
        List<Map<String, Object>> cierres = new ArrayList<>();
        for (int i = 0; i < dias; i++) {
            reloj.avanzar(Duration.ofDays(1));
            LocalDate ayer = reloj.hoy().minusDays(1);
            Map<String, Object> cierre = new LinkedHashMap<>();
            cierre.put("dia", ayer);
            cofre.cerrarDia(ayer);
            try {
                var c = pagos.conciliar(ayer);
                cierre.put("conciliados", c.conciliados());
                cierre.put("diferencias", c.diferencias());
            } catch (RuntimeException e) {
                log.warn("Conciliación demo del {} no disponible: {}", ayer, e.getMessage());
                cierre.put("conciliacion", "no disponible");
            }
            cierres.add(cierre);
        }
        return Map.of("fechaActual", reloj.hoy(), "cierres", cierres);
    }
}
