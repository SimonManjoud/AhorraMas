package ve.impulsatech.ahorramas.simuladores.bcv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import ve.impulsatech.ahorramas.simuladores.PruebaBase;
import ve.impulsatech.ahorramas.simuladores.comun.Tiempo;

class BcvTest extends PruebaBase {

    @Autowired
    Tiempo tiempo;

    @Test
    void tasaBaseDel31DeDiciembre() throws Exception {
        mvc.perform(get("/bcv/tasas").param("moneda", "USD").param("desde", "2025-12-31").param("hasta", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].valorBsX10000").value(3013700))
                .andExpect(jsonPath("$[0].fuente").value("BCV"))
                .andExpect(jsonPath("$[0].fechaHoraPublicacion").value("2025-12-31T08:00:00-04:00"));
    }

    @Test
    void serieCasiMonotonaYFinesDeSemanaRepitenElViernes() throws Exception {
        MvcResult r = mvc.perform(get("/bcv/tasas").param("moneda", "USD")
                        .param("desde", "2026-09-01").param("hasta", "2026-10-31"))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> serie = leer(r, "$");
        assertThat(serie).hasSize(61);

        long primero = valor(serie.getFirst());
        long ultimo = valor(serie.getLast());
        assertThat(ultimo).isGreaterThan(primero);
        for (int i = 1; i < serie.size(); i++) {
            LocalDate fecha = LocalDate.parse((String) serie.get(i).get("fecha"));
            assertThat(fecha).isEqualTo(LocalDate.parse((String) serie.get(i - 1).get("fecha")).plusDays(1));
            long anterior = valor(serie.get(i - 1));
            long actual = valor(serie.get(i));
            // nunca baja más de 0,2 % de un día a otro (ruido ±0,08 % sobre +0,18 %/día)
            assertThat(actual * 1000).isGreaterThanOrEqualTo(anterior * 998);
            if (fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY) {
                Map<String, Object> viernes = serie.stream()
                        .filter(t -> LocalDate.parse((String) t.get("fecha")).equals(
                                fecha.with(java.time.temporal.TemporalAdjusters.previous(DayOfWeek.FRIDAY))))
                        .findFirst().orElseThrow();
                assertThat(actual).isEqualTo(valor(viernes));
            }
        }
    }

    @Test
    void usdtUsaLaMismaTasaQueUsdYLaMonedaSeValida() throws Exception {
        long usd = ((Number) leer(mvc.perform(get("/bcv/tasas/hoy").param("moneda", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(tiempo.hoy().toString()))
                .andReturn(), "$.valorBsX10000")).longValue();
        mvc.perform(get("/bcv/tasas/hoy").param("moneda", "USDT"))
                .andExpect(jsonPath("$.moneda").value("USDT"))
                .andExpect(jsonPath("$.valorBsX10000").value(usd));
        mvc.perform(get("/bcv/tasas/hoy").param("moneda", "EUR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("MONEDA_NO_SOPORTADA"));
    }

    @Test
    void controlDemoFuerzaLaTasaDelDia() throws Exception {
        postJson("/simulador/bcv/tasa-hoy", "{\"valorBsX10000\": 5100000}").andExpect(status().isOk());
        mvc.perform(get("/bcv/tasas/hoy").param("moneda", "USD"))
                .andExpect(jsonPath("$.valorBsX10000").value(5100000));
        mvc.perform(delete("/simulador/bcv/tasa-hoy")).andExpect(status().isOk());
        mvc.perform(get("/bcv/tasas/hoy").param("moneda", "USD"))
                .andExpect(jsonPath("$.valorBsX10000").value(
                        BcvService.valorGenerado(BcvService.diaHabil(tiempo.hoy()))));
    }

    @Test
    void inflacionPublicadaYMesEnCursoNoPublicado() throws Exception {
        MvcResult r = mvc.perform(get("/bcv/inflacion").param("anio", "2026").param("mes", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaPublicacion").value("2026-11-03"))
                .andExpect(jsonPath("$.fuente").value("BCV"))
                .andReturn();
        int pb = leer(r, "$.variacionMensualPb");
        assertThat(pb).isBetween(800, 1800);

        YearMonth actual = YearMonth.from(tiempo.hoy());
        mvc.perform(get("/bcv/inflacion").param("anio", String.valueOf(actual.getYear()))
                        .param("mes", String.valueOf(actual.getMonthValue())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("INFLACION_NO_PUBLICADA"));

        MvcResult lista = mvc.perform(get("/bcv/inflacion").param("desde", "2026-01"))
                .andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> meses = leer(lista, "$");
        assertThat(meses).isNotEmpty();
        assertThat(meses.getFirst().get("mes")).isEqualTo(1);
        assertThat(meses).allSatisfy(m -> assertThat((Integer) m.get("variacionMensualPb")).isBetween(800, 1800));
        Map<String, Object> ultimo = meses.getLast();
        assertThat(YearMonth.of((Integer) ultimo.get("anio"), (Integer) ultimo.get("mes"))).isBefore(actual);
    }

    private static long valor(Map<String, Object> tasa) {
        return ((Number) tasa.get("valorBsX10000")).longValue();
    }
}
