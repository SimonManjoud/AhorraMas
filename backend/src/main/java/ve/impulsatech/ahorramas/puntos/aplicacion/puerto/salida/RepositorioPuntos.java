package ve.impulsatech.ahorramas.puntos.aplicacion.puerto.salida;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles.Nivel;
import ve.impulsatech.ahorramas.puntos.dominio.Niveles.Recompensa;
import ve.impulsatech.ahorramas.puntos.dominio.TransaccionPuntos;

/** Puerto de persistencia del esquema {@code puntos}. */
public interface RepositorioPuntos {

    long cuentaUsuario(long idUsuario, OffsetDateTime ahora);

    long cuentaFondeoBanco(long idInstitucion, OffsetDateTime ahora);

    long cuentaComercio(long idComercio, OffsetDateTime ahora);

    long cuentaSistema();

    boolean existeClave(String claveOrigen);

    /** Inserta la transacción con sus asientos (el trigger diferido verifica la partida doble). */
    long registrar(TransaccionPuntos t);

    long saldo(long idCuentaPuntos);

    long totalCreditos(long idCuentaPuntos);

    List<Movimiento> movimientos(long idCuentaPuntos, int limite);

    List<Nivel> niveles();

    Optional<NivelVigente> nivelVigente(long idUsuario);

    void cambiarNivel(long idUsuario, int idNivel, OffsetDateTime ahora);

    List<Recompensa> recompensas();

    Optional<Recompensa> recompensaParaActualizar(long idRecompensa);

    void descontarStock(long idRecompensa);

    long insertarCanje(long idRecompensa, long idUsuario, long idTransaccion, long puntos, String codigo, OffsetDateTime ahora);

    List<Canje> canjes(long idUsuario);

    record Movimiento(long idTransaccion, OffsetDateTime fechaHora, String concepto, String descripcion, long puntos) {
    }

    record NivelVigente(Nivel nivel, OffsetDateTime desde) {
    }

    record Canje(long idCanje, String recompensa, String codigoCanje, long puntosCanjeados, String estatus, OffsetDateTime fechaHora) {
    }
}
