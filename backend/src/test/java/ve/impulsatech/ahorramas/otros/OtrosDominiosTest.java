package ve.impulsatech.ahorramas.otros;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import ve.impulsatech.ahorramas.backoffice.dominio.ReglasAml;
import ve.impulsatech.ahorramas.compartido.dominio.ErrorNegocio;
import ve.impulsatech.ahorramas.identidad.dominio.Usuario;
import ve.impulsatech.ahorramas.perfilia.dominio.ClasificadorPerfil;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles;
import ve.impulsatech.ahorramas.puntos.dominio.TransaccionPuntos;

/** Reglas de Identidad (R-06), Puntos (R-21/R-22), IA (R-23) y AML. */
class OtrosDominiosTest {

    @Test
    void registroExigeMayoriaDeEdadYFormatoVenezolano() {
        OffsetDateTime hoy = OffsetDateTime.parse("2026-11-04T10:00:00-04:00");
        assertThatThrownBy(() -> Usuario.nuevo("V", 30111222, "Ana", null, "Ruiz", null, LocalDate.of(2010, 1, 1), "a@b.com", "04141234567", 1, hoy))
                .hasMessageContaining("mayor de edad");
        assertThatThrownBy(() -> Usuario.nuevo("V", 30111222, "Ana", null, "Ruiz", null, LocalDate.of(1990, 1, 1), "a@b.com", "02121234567", 1, hoy))
                .hasMessageContaining("Teléfono");
        Usuario u = Usuario.nuevo("V", 30111222, "Ana", null, "Ruiz", null, LocalDate.of(1990, 1, 1), " A@B.com ", "0414-123.45.67", 1, hoy);
        assertThat(u.correo()).isEqualTo("a@b.com");
        assertThat(u.telefonoMovil()).isEqualTo("04141234567");
        assertThatThrownBy(() -> Usuario.validarPin("12")).isInstanceOf(ErrorNegocio.class);
        assertThatThrownBy(() -> Usuario.validarPin("1234")).hasMessageContaining("6 dígitos");
        Usuario.validarPin("123456");
    }

    @Test
    void transaccionDePuntosDebeCuadrar() {
        OffsetDateTime ahora = OffsetDateTime.now();
        assertThatThrownBy(() -> new TransaccionPuntos(ahora, "meta", null, null,
                List.of(new TransaccionPuntos.Asiento(1, "debito", 10), new TransaccionPuntos.Asiento(2, "credito", 9))))
                .hasMessageContaining("descuadrada");
        assertThatThrownBy(() -> new TransaccionPuntos(ahora, "meta", null, null, List.of(new TransaccionPuntos.Asiento(1, "debito", 10))))
                .hasMessageContaining("dos asientos");
        assertThat(TransaccionPuntos.transferencia(1, 2, 50, "meta", "x", "k", ahora).asientos()).hasSize(2);
    }

    @Test
    void nivelesYCanje() {
        List<Niveles.Nivel> niveles = List.of(new Niveles.Nivel(1, "Base", 0, 1), new Niveles.Nivel(2, "Gold", 5_000, 2),
                new Niveles.Nivel(3, "Platinum", 20_000, 3));
        assertThat(Niveles.nivelPara(4_999, niveles).nombre()).isEqualTo("Base");
        assertThat(Niveles.nivelPara(5_000, niveles).nombre()).isEqualTo("Gold");
        var gold = new Niveles.Recompensa(1, "Cine", 2_500, "Cines", 1, niveles.get(1), LocalDate.of(2026, 1, 1), null, 10);
        assertThatThrownBy(() -> Niveles.validarCanje(gold, 10_000, niveles.get(0), LocalDate.of(2026, 11, 4))).hasMessageContaining("Gold");
        assertThatThrownBy(() -> Niveles.validarCanje(gold, 100, niveles.get(1), LocalDate.of(2026, 11, 4))).hasMessageContaining("faltan");
        Niveles.validarCanje(gold, 2_500, niveles.get(2), LocalDate.of(2026, 11, 4));
    }

    @Test
    void clasificadorDePerfilSumaCienPorCiento() {
        for (String obj : List.of("proteger", "meta_corta", "crecer")) {
            for (String tol : List.of("baja", "media", "alta")) {
                var c = ClasificadorPerfil.clasificar(new ClasificadorPerfil.Rasgos(obj, 12, tol, "R3", 1_000, 1));
                assertThat(c.distribucionPb().values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(10_000);   // R-23
            }
        }
        assertThat(ClasificadorPerfil.clasificar(new ClasificadorPerfil.Rasgos("proteger", 3, "baja", "R1", 5_000, 0)).perfil())
                .isEqualTo("conservador");
        assertThat(ClasificadorPerfil.clasificar(new ClasificadorPerfil.Rasgos("crecer", 36, "alta", "R5", 500, 2)).perfil())
                .isEqualTo("dinamico");
        assertThat(ClasificadorPerfil.rangoIngreso(25_000)).isEqualTo("R2");
    }

    @Test
    void reglasAml() {
        assertThat(ReglasAml.evaluarPago(50_000, 50_000, 1)).isEmpty();
        assertThat(ReglasAml.evaluarPago(600_000, 600_000, 1)).extracting(ReglasAml.Alerta::nivelRiesgo).contains("alto", "medio");
        assertThat(ReglasAml.evaluarPago(10_000, 40_000, 4)).extracting(ReglasAml.Alerta::regla).containsExactly("AML-03-FRACCIONAMIENTO");
    }
}
