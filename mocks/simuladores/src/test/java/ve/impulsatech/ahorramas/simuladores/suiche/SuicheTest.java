package ve.impulsatech.ahorramas.simuladores.suiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import ve.impulsatech.ahorramas.simuladores.PruebaBase;
import ve.impulsatech.ahorramas.simuladores.comun.Semilla;
import ve.impulsatech.ahorramas.simuladores.comun.Tiempo;
import ve.impulsatech.ahorramas.simuladores.instituciones.BancoService;
import ve.impulsatech.ahorramas.simuladores.instituciones.ObDtos.Tarjeta;
import ve.impulsatech.ahorramas.simuladores.suiche.SuicheDtos.RegistroLiquidacion;

class SuicheTest extends PruebaBase {

    private static final String CEDULA = "V12345678";

    @Autowired
    BancoService banco;
    @Autowired
    Tiempo tiempo;

    private String refCuenta;
    private String refTarjeta;

    @BeforeEach
    void datos() {
        refCuenta = banco.cuentas("0901", CEDULA).getFirst().refCuenta();
        refTarjeta = banco.tarjetas("0902", CEDULA).getFirst().refTarjeta();
    }

    @Test
    void consultaDeBeneficiario() throws Exception {
        postJson("/suiche/beneficiarios/consulta", """
                {"bancoEmisor":"0902","refTarjeta":"%s"}""".formatted(refTarjeta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refTarjeta").value(refTarjeta))
                .andExpect(jsonPath("$.titular").isString())
                .andExpect(jsonPath("$.ultimos4").isString());
        postJson("/suiche/beneficiarios/consulta", """
                {"bancoEmisor":"0902","refTarjeta":"TDC-0902-zzzz"}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("TARJETA_NO_EXISTE"));
    }

    @Test
    void caminoFelizDebitaCuentaYAbonaTdcYElReversoLoDeshace() throws Exception {
        long saldoAntes = banco.saldo("0901", CEDULA, refCuenta).saldoCts();
        long tdcAntes = saldoTdc();
        String ref = UUID.randomUUID().toString();

        MvcResult aut = autorizar(ref, 150_000).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("aprobado"))
                .andExpect(jsonPath("$.codigoRespuesta").value("00"))
                .andReturn();
        String codigoAut = leer(aut, "$.codigoAutorizacion");
        assertThat(codigoAut).startsWith("AUT");
        assertThat((String) leer(aut, "$.referenciaSuiche")).matches("S7B-\\d{8}-\\d{6}");

        captura(ref, codigoAut).andExpect(jsonPath("$.resultado").value("aprobado"))
                .andExpect(jsonPath("$.codigoRespuesta").value("00"));
        mvc.perform(get("/suiche/operaciones/" + ref)).andExpect(jsonPath("$.estado").value("capturado"))
                .andExpect(jsonPath("$.montoCts").value(150_000));
        assertThat(banco.saldo("0901", CEDULA, refCuenta).saldoCts()).isEqualTo(saldoAntes - 150_000);
        assertThat(saldoTdc()).isEqualTo(tdcAntes - 150_000);

        postJson("/suiche/reversos", """
                {"referencia":"%s","motivo":"Prueba"}""".formatted(ref))
                .andExpect(jsonPath("$.resultado").value("aprobado"));
        mvc.perform(get("/suiche/operaciones/" + ref)).andExpect(jsonPath("$.estado").value("reversado"));
        assertThat(banco.saldo("0901", CEDULA, refCuenta).saldoCts()).isEqualTo(saldoAntes);
        assertThat(saldoTdc()).isEqualTo(tdcAntes);
    }

    @Test
    void repetirConLaMismaReferenciaEsIdempotente() throws Exception {
        long saldoAntes = banco.saldo("0901", CEDULA, refCuenta).saldoCts();
        String ref = UUID.randomUUID().toString();
        String primera = cuerpo(autorizar(ref, 20_000).andReturn());
        String segunda = cuerpo(autorizar(ref, 20_000).andReturn());
        assertThat(segunda).isEqualTo(primera);

        String codigoAut = com.jayway.jsonpath.JsonPath.read(primera, "$.codigoAutorizacion");
        String c1 = cuerpo(captura(ref, codigoAut).andReturn());
        String c2 = cuerpo(captura(ref, codigoAut).andReturn());
        assertThat(c2).isEqualTo(c1);
        // un solo débito
        assertThat(banco.saldo("0901", CEDULA, refCuenta).saldoCts()).isEqualTo(saldoAntes - 20_000);
    }

    @Test
    void escenario51RechazaPorFondosInsuficientes() throws Exception {
        String ref = UUID.randomUUID().toString();
        autorizar(ref, 10_051).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("rechazado"))
                .andExpect(jsonPath("$.codigoRespuesta").value("51"))
                .andExpect(jsonPath("$.motivo").value("Fondos insuficientes"));
        mvc.perform(get("/suiche/operaciones/" + ref)).andExpect(jsonPath("$.estado").value("rechazado"));
    }

    @Test
    void escenario77RechazaLaCapturaYSeDebeReversar() throws Exception {
        long saldoAntes = banco.saldo("0901", CEDULA, refCuenta).saldoCts();
        String ref = UUID.randomUUID().toString();
        String codigoAut = leer(autorizar(ref, 10_077).andExpect(jsonPath("$.resultado").value("aprobado"))
                .andReturn(), "$.codigoAutorizacion");
        captura(ref, codigoAut)
                .andExpect(jsonPath("$.resultado").value("rechazado"))
                .andExpect(jsonPath("$.codigoRespuesta").value("05"))
                .andExpect(jsonPath("$.motivo").value("Operación no permitida por el emisor"));
        mvc.perform(get("/suiche/operaciones/" + ref)).andExpect(jsonPath("$.estado").value("autorizado"));
        assertThat(banco.saldo("0901", CEDULA, refCuenta).saldoCts()).isEqualTo(saldoAntes);

        postJson("/suiche/reversos", """
                {"referencia":"%s","motivo":"Captura rechazada"}""".formatted(ref))
                .andExpect(jsonPath("$.resultado").value("aprobado"));
        mvc.perform(get("/suiche/operaciones/" + ref)).andExpect(jsonPath("$.estado").value("reversado"));
    }

    @Test
    void escenario13TimeoutQuedaAutorizadoAntesDeResponder() throws Exception {
        String ref = UUID.randomUUID().toString();
        long inicio = System.nanoTime();
        CompletableFuture<MvcResult> lenta = CompletableFuture.supplyAsync(() -> {
            try {
                return autorizar(ref, 10_013).andReturn();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // mientras la respuesta "tarda", la consulta ya encuentra la operación autorizada
        Thread.sleep(120);
        assertThat(lenta).isNotDone();
        mvc.perform(get("/suiche/operaciones/" + ref))
                .andExpect(jsonPath("$.estado").value("autorizado"))
                .andExpect(jsonPath("$.codigoAutorizacion").isString());

        MvcResult r = lenta.get(5, TimeUnit.SECONDS);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio)).isGreaterThanOrEqualTo(300);
        assertThat((String) leer(r, "$.resultado")).isEqualTo("aprobado");

        // el reintento con la misma referencia responde de inmediato lo mismo
        long t0 = System.nanoTime();
        assertThat(cuerpo(autorizar(ref, 10_013).andReturn())).isEqualTo(cuerpo(r));
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0)).isLessThan(300);
    }

    @Test
    void consultaDeOperacionInexistente() throws Exception {
        mvc.perform(get("/suiche/operaciones/no-llego-nunca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("no_existe"));
        postJson("/suiche/capturas", """
                {"referencia":"no-llego-nunca","codigoAutorizacion":"AUT000000"}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("OPERACION_NO_EXISTE"));
    }

    @Test
    void archivoDeLiquidacionConEscenarios98y99() throws Exception {
        String normal = pagar(30_000);
        String falta = pagar(30_098);
        String distinto = pagar(30_099);

        String fecha = tiempo.hoy().toString();
        MvcResult r = mvc.perform(get("/suiche/liquidaciones/" + fecha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaLiquidacion").value(fecha))
                .andExpect(jsonPath("$.nombreArchivo").value("LIQ_S7B_" + fecha.replace("-", "") + ".json"))
                .andReturn();

        List<Map<String, Object>> registros = leer(r, "$.registros");
        List<String> referencias = registros.stream().map(m -> (String) m.get("referencia")).toList();
        assertThat(referencias).contains(normal, distinto).doesNotContain(falta);
        Map<String, Object> regNormal = registros.get(referencias.indexOf(normal));
        Map<String, Object> regDistinto = registros.get(referencias.indexOf(distinto));
        assertThat(((Number) regNormal.get("montoCts")).longValue()).isEqualTo(30_000);
        assertThat(((Number) regDistinto.get("montoCts")).longValue()).isEqualTo(30_099 + 100);
        assertThat(regNormal.get("estado")).isEqualTo("capturado");

        int total = leer(r, "$.totalRegistros");
        assertThat(total).isEqualTo(registros.size());
        long suma = registros.stream().mapToLong(m -> ((Number) m.get("montoCts")).longValue()).sum();
        assertThat(((Number) leer(r, "$.montoTotalCts")).longValue()).isEqualTo(suma);
        for (int i = 0; i < registros.size(); i++) {
            assertThat(registros.get(i).get("linea")).isEqualTo(i + 1);
        }

        // hash = SHA-256 del JSON canónico de los registros
        List<RegistroLiquidacion> tipados = registros.stream().map(m -> new RegistroLiquidacion(
                (Integer) m.get("linea"), (String) m.get("referencia"), (String) m.get("referenciaSuiche"),
                ((Number) m.get("montoCts")).longValue(), (String) m.get("estado"),
                (String) m.get("fechaHoraOperacion"))).toList();
        String hash = leer(r, "$.hashArchivo");
        assertThat(hash).matches("[0-9a-f]{64}").isEqualTo(Semilla.sha256Hex(SuicheService.jsonCanonico(tipados)));
        // determinista: el mismo día sin cambios ⇒ mismo archivo
        assertThat(cuerpo(mvc.perform(get("/suiche/liquidaciones/" + fecha)).andReturn())).isEqualTo(cuerpo(r));
    }

    private String pagar(long monto) throws Exception {
        String ref = UUID.randomUUID().toString();
        String codigoAut = leer(autorizar(ref, monto).andReturn(), "$.codigoAutorizacion");
        captura(ref, codigoAut).andExpect(jsonPath("$.resultado").value("aprobado"));
        return ref;
    }

    private long saldoTdc() {
        Tarjeta t = banco.tarjetas("0902", CEDULA).getFirst();
        return t.estadoCuenta().saldoTotalCts();
    }

    private org.springframework.test.web.servlet.ResultActions autorizar(String ref, long monto) throws Exception {
        return postJson("/suiche/autorizaciones", """
                {"referencia":"%s","bancoOrigen":"0901","refCuentaOrigen":"%s","bancoEmisor":"0902",
                 "refTarjeta":"%s","montoCts":%d,"moneda":"VES"}""".formatted(ref, refCuenta, refTarjeta, monto));
    }

    private org.springframework.test.web.servlet.ResultActions captura(String ref, String codigoAut) throws Exception {
        return postJson("/suiche/capturas", """
                {"referencia":"%s","codigoAutorizacion":"%s"}""".formatted(ref, codigoAut));
    }
}
