package ve.impulsatech.ahorramas.puntos.adaptador.entrada.eventos;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.AporteRegistrado;
import ve.impulsatech.ahorramas.cofre.api.EventosCofre.MetaCumplida;
import ve.impulsatech.ahorramas.compartido.dominio.Moneda;
import ve.impulsatech.ahorramas.compartido.dominio.Montos;
import ve.impulsatech.ahorramas.identidad.api.EventosIdentidad.UsuarioRegistrado;
import ve.impulsatech.ahorramas.pagos.api.EventosPagos.PagoCapturado;
import ve.impulsatech.ahorramas.puntos.aplicacion.puerto.entrada.CasosDeUsoPuntos.AcreditarPuntos;

/** Los buenos hábitos de otros módulos se convierten en puntos (eventos persistidos, idempotentes). */
@Component
class OyentesPuntos {

    static final long BONO_META_CUMPLIDA = 500;

    private final AcreditarPuntos puntos;

    OyentesPuntos(AcreditarPuntos puntos) {
        this.puntos = puntos;
    }

    @ApplicationModuleListener
    void alRegistrarUsuario(UsuarioRegistrado e) {
        puntos.abrirCuenta(e.idUsuario());
    }

    @ApplicationModuleListener
    void alAportar(AporteRegistrado e) {
        puntos.acreditarDesdeSistema(e.idUsuario(), e.puntosBase(), "meta",
                "Aporte de " + Montos.formatear(e.montoUsdCts(), Moneda.USD) + " a «" + e.nombreMeta() + "»", "aporte:" + e.idAporte());
        if (e.bonoRacha() > 0) {
            puntos.acreditarDesdeSistema(e.idUsuario(), e.bonoRacha(), "racha",
                    "Bono de racha x" + (e.multiplicadorX100() / 100.0), "aporte:" + e.idAporte() + ":racha");
        }
    }

    @ApplicationModuleListener
    void alCumplirMeta(MetaCumplida e) {
        puntos.acreditarDesdeSistema(e.idUsuario(), BONO_META_CUMPLIDA, "meta", "¡Meta cumplida! «" + e.nombreMeta() + "»",
                "meta:" + e.idMeta() + ":cumplida");
    }

    /** R-18: solo el pago puntual genera puntos (financiados por el banco aliado). */
    @ApplicationModuleListener
    void alCapturarPago(PagoCapturado e) {
        if (e.esPuntual() && e.puntosPorPuntualidad() > 0) {
            puntos.acreditarDesdeBanco(e.idUsuario(), e.idInstitucionEmisor(), e.puntosPorPuntualidad(), "Pago puntual de tu TDC",
                    "pago:" + e.idPago());
        }
    }
}
