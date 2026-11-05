package ve.impulsatech.ahorramas.compartido.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TasaBcvTest {

    private final TasaBcv tasa = new TasaBcv(LocalDate.of(2026, 11, 4), 4_987_321); // 498,7321 Bs/USD

    @Test
    void convierteUsdABsConAritmeticaEnteraYRedondeoHalfUp() {
        // $2,40 × 498,7321 = Bs 1.196,95704 → Bs 1.196,96
        assertThat(tasa.usdABs(240)).isEqualTo(119_696);
        assertThat(tasa.usdABs(0)).isZero();
    }

    @Test
    void convierteBsAUsd() {
        // Bs 15.430,22 / 498,7321 = $30,938... → $30,94
        assertThat(tasa.bsAUsd(1_543_022)).isEqualTo(3_094);
    }

    @Test
    void convierteSegunMoneda() {
        assertThat(tasa.aBs(100, Moneda.VES)).isEqualTo(100);
        assertThat(tasa.aUsd(100, Moneda.USDT)).isEqualTo(100);
        assertThat(tasa.aBs(100, Moneda.USD)).isEqualTo(49_873);
    }

    @Test
    void calculaDevaluacionEnPuntosBasicos() {
        TasaBcv inicioDeAnio = new TasaBcv(LocalDate.of(2025, 1, 1), 520_200);   // 52,02
        TasaBcv finDeAnio = new TasaBcv(LocalDate.of(2025, 12, 31), 3_013_700);  // 301,37
        // (301,37 − 52,02) / 52,02 = 479,3 %
        assertThat(finDeAnio.variacionPbDesde(inicioDeAnio)).isEqualTo(47_933);
    }

    @Test
    void rechazaTasaNoPositiva() {
        assertThatThrownBy(() -> new TasaBcv(LocalDate.now(), 0)).isInstanceOf(ErrorNegocio.class);
    }

    @Test
    void redondeaNegativosDeFormaSimetrica() {
        assertThat(TasaBcv.dividirRedondeando(java.math.BigInteger.valueOf(-15), java.math.BigInteger.TEN)).isEqualTo(-2);
        assertThat(TasaBcv.dividirRedondeando(java.math.BigInteger.valueOf(15), java.math.BigInteger.TEN)).isEqualTo(2);
        assertThat(TasaBcv.dividirRedondeando(java.math.BigInteger.valueOf(14), java.math.BigInteger.TEN)).isEqualTo(1);
    }

    @Test
    void formateaMontosParaElUsuario() {
        assertThat(Montos.formatear(123_456, Moneda.VES)).isEqualTo("Bs 1.234,56");
        assertThat(Montos.formatear(240, Moneda.USD)).isEqualTo("$2,40");
        assertThat(Montos.porcentajePb(10_000, 1_250)).isEqualTo(1_250);
        assertThat(Montos.dividirHaciaArriba(10, 3)).isEqualTo(4);
    }
}
