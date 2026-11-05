package ve.impulsatech.ahorramas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import ve.impulsatech.ahorramas.cofre.api.CofreApi;
import ve.impulsatech.ahorramas.compartido.infraestructura.RelojAjustable;
import ve.impulsatech.ahorramas.consolidacion.aplicacion.puerto.salida.PuertosConsolidacion.MovimientoExterno;
import ve.impulsatech.ahorramas.soporte.PruebaIntegracion;

/** M5 · Cofre Protector de punta a punta: Ruta del día, aportes, reglas, racha con colchón, sello y Espejo. */
class CofreProtectorIntegracionTest extends PruebaIntegracion {

    @Autowired
    RelojAjustable reloj;
    @Autowired
    CofreApi cofre;

    @AfterEach
    void reiniciarReloj() {
        reloj.reiniciar();
    }

    private long crearMeta(UsuarioPrueba u, long objetivo) {
        var r = u.api().post("/api/v1/metas", Map.of("nombre", "Viaje en marzo", "categoria", "viaje", "montoObjetivoUsdCts", objetivo,
                "fechaObjetivo", reloj.hoy().plusDays(100).toString(), "frecuenciaCuota", "diaria"));
        assertThat(r.status()).as("meta: %s", r.cuerpo()).isEqualTo(201);
        return r.largo("idMeta");
    }

    @Test
    void rutaDelDiaYAporteDeLaCuotaSumanRachaYPuntos() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = crearMeta(u, 30_000);
        var ruta = u.api().get("/api/v1/metas/" + meta + "/ruta-del-dia");
        assertThat(ruta.largo("periodosRestantes")).isEqualTo(100);
        assertThat(ruta.largo("montoSugeridoUsdCts")).isEqualTo(300);           // ⌈30000/100⌉
        assertThat(ruta.texto("mensaje")).startsWith("Hoy toca apartar $3,00");
        assertThat(ruta.texto("estatus")).isEqualTo("pendiente");

        var ap = u.api().post("/api/v1/metas/" + meta + "/aportes", Map.of("montoUsdCts", 300, "origen", "cuota_del_dia"));
        assertThat(ap.status()).as("aporte: %s", ap.cuerpo()).isEqualTo(201);
        assertThat(ap.largo("meta.acumuladoUsdCts")).isEqualTo(300);
        assertThat(ap.texto("meta.cuotaHoy.estatus")).isEqualTo("cumplida");
        assertThat(ap.largo("racha.diasConsecutivos")).isEqualTo(1);
        assertThat(ap.get("racha.aporteHoy")).isEqualTo(true);
        assertThat(ap.largo("puntosGanados")).isEqualTo(13);                     // 10 base del día + 3 por los $3

        esperar(() -> assertThat(u.api().get("/api/v1/puntos").largo("saldo")).isEqualTo(13));
        // La cuota del día no cambia durante el día (se fija una sola vez)
        assertThat(u.api().get("/api/v1/metas/" + meta + "/ruta-del-dia").largo("montoSugeridoUsdCts")).isEqualTo(300);
    }

    @Test
    void reglaDePorcentajeDeIngresoAportaSolaAlLlegarLaNomina() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = crearMeta(u, 100_000);
        var sinPin = u.api().post("/api/v1/metas/" + meta + "/reglas", Map.of("tipo", "pct_ingreso", "porcentajePb", 1_000));
        assertThat(sinPin.texto("codigo")).isEqualTo("STEP_UP_REQUERIDO");   // R-13: solo con huella/PIN
        u.api().conStepUp(stepUp(u, false));
        assertThat(u.api().post("/api/v1/metas/" + meta + "/reglas", Map.of("tipo", "pct_ingreso", "porcentajePb", 1_000)).status()).isEqualTo(201);
        u.api().conStepUp(null);

        openBanking.inyectar("0901", new MovimientoExterno("NOMINA-" + u.cedula(), OffsetDateTime.now(ZoneId.of("America/Caracas")).plusSeconds(5),
                "credito", 20_000_000, "PAGO NOMINA", "ingreso"));
        assertThat(u.api().post("/api/v1/consolidado/sincronizar", null).status()).isEqualTo(202);

        esperar(() -> {
            var aportes = u.api().get("/api/v1/metas/" + meta + "/aportes").lista();
            assertThat(aportes).hasSize(1);
            assertThat(aportes.getFirst().get("origen")).isEqualTo("regla");
            assertThat(((Number) aportes.getFirst().get("montoBsCts")).longValue()).isBetween(1_999_000L, 2_001_000L);   // 10 % de Bs 200.000
        });
        // idempotencia del worker: volver a sincronizar no duplica el aporte de la regla
        u.api().post("/api/v1/consolidado/sincronizar", null);
        assertThat(u.api().get("/api/v1/metas/" + meta + "/aportes").lista()).hasSize(1);
    }

    @Test
    void elSelloSoloCuestaElMultiplicadorNuncaDinero() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = crearMeta(u, 50_000);
        u.api().post("/api/v1/metas/" + meta + "/aportes", Map.of("montoUsdCts", 2_000, "origen", "manual"));
        u.api().conStepUp(stepUp(u, false));
        assertThat(u.api().post("/api/v1/metas/" + meta + "/sello", Map.of("montoSelladoUsdCts", 5_000)).texto("codigo")).isEqualTo("R-12");
        var sello = u.api().post("/api/v1/metas/" + meta + "/sello", Map.of("montoSelladoUsdCts", 1_500));
        assertThat(sello.status()).isEqualTo(201);
        assertThat(sello.texto("fechaLiberacion")).isEqualTo(reloj.hoy().plusDays(100).toString());

        var retiro = u.api().post("/api/v1/metas/" + meta + "/retiros", Map.of("montoUsdCts", 1_000));
        assertThat(retiro.status()).isEqualTo(200);
        assertThat(retiro.get("selloRoto")).isEqualTo(true);
        assertThat(retiro.largo("meta.acumuladoUsdCts")).isEqualTo(1_000);     // el dinero sigue siendo del usuario
        assertThat(retiro.texto("meta.sello.estatus")).isEqualTo("roto");
        assertThat(u.api().get("/api/v1/racha").largo("diasConsecutivos")).isZero();   // perdió la racha y su multiplicador
        String motivo = jdbc.sql("SELECT motivo_cierre FROM cofre.racha WHERE id_usuario = :u").param("u", u.idUsuario()).query(String.class).single();
        assertThat(motivo).isEqualTo("sello_roto");
    }

    @Test
    void unDiaSinAporteUsaUnColchonYSinColchonesLaRachaSeCierra() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = crearMeta(u, 50_000);
        u.api().post("/api/v1/metas/" + meta + "/aportes", Map.of("montoUsdCts", 100, "origen", "manual"));

        // Día 1 sin aporte → el cierre usa un día colchón: la racha sigue viva
        reloj.avanzar(Duration.ofDays(2));
        cofre.cerrarDia(reloj.hoy().minusDays(1));
        var racha = u.api().get("/api/v1/racha");
        assertThat(racha.largo("diasConsecutivos")).isEqualTo(2);
        assertThat(racha.largo("diasColchonUsados")).isEqualTo(1);
        assertThat((List<Map<String, Object>>) racha.get("ultimosDias")).extracting(d -> d.get("tipo")).contains("colchon", "aporte");

        // Sin aportar, al agotarse los colchones la racha se cierra (sin_aporte)
        for (int i = 0; i < 10 && u.api().get("/api/v1/racha").get("idRacha") != null; i++) {
            reloj.avanzar(Duration.ofDays(1));
            cofre.cerrarDia(reloj.hoy().minusDays(1));
        }
        assertThat(u.api().get("/api/v1/racha").get("idRacha")).isNull();
        String motivo = jdbc.sql("SELECT motivo_cierre FROM cofre.racha WHERE id_usuario = :u").param("u", u.idUsuario()).query(String.class).single();
        assertThat(motivo).isEqualTo("sin_aporte");
    }

    @Test
    void espejoDeImpactoProvisionalDelMesConDisclaimer() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = crearMeta(u, 50_000);
        u.api().post("/api/v1/metas/" + meta + "/aportes", Map.of("montoUsdCts", 12_000, "origen", "manual"));
        var e = u.api().get("/api/v1/espejo-impacto");
        assertThat(e.status()).isEqualTo(200);
        assertThat(e.get("provisional")).isEqualTo(true);
        assertThat(e.largo("saldoCofresUsdCts")).isEqualTo(12_000);
        assertThat(e.largo("perdidaEvitadaBsCts")).isGreaterThanOrEqualTo(0);
        assertThat(e.texto("disclaimer")).contains("no constituye asesoría");
        assertThat(e.texto("frase")).contains("$120,00");
        assertThat(u.api().get("/api/v1/espejo-impacto?anio=2099&mes=1").status()).isEqualTo(400);
    }

    @Test
    void laMetaSeCumpleYOtorgaBono() {
        UsuarioPrueba u = usuarioConBancos();
        long meta = crearMeta(u, 1_000);
        var ap = u.api().post("/api/v1/metas/" + meta + "/aportes", Map.of("montoUsdCts", 1_000, "origen", "manual"));
        assertThat(ap.texto("meta.estatus")).isEqualTo("cumplida");
        assertThat(ap.largo("meta.progresoPb")).isEqualTo(10_000);
        esperar(() -> assertThat(u.api().get("/api/v1/puntos/movimientos").lista())
                .extracting(m -> m.get("descripcion").toString()).anyMatch(d -> d.startsWith("¡Meta cumplida!")));
    }
}
