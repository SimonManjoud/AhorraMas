package ve.impulsatech.ahorramas.backoffice.aplicacion.puerto.salida;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Persistencia del esquema {@code backoffice}. */
public interface RepositorioBackoffice {

    Optional<Operador> operadorPorCorreo(String correo);

    void asegurarOperador(String nombre, String apellido, String correo, String rol, String claveHash);

    /** Inserta un caso; vacío si la clave de origen ya existía (evento repetido). */
    Optional<Long> abrirCaso(Long idUsuario, Long idPago, String tipo, String prioridad, String descripcion, String claveOrigen, OffsetDateTime ahora);

    List<Caso> casos(String estatus, int limite);

    Optional<Caso> caso(long id);

    void actualizarCaso(long id, String estatus, String resolucion, long idOperador, OffsetDateTime cierre);

    Optional<Long> crearAlerta(long idUsuario, Long idPago, String regla, String nivel, String claveOrigen, OffsetDateTime ahora);

    List<Alerta> alertas(String estatus, int limite);

    Optional<Alerta> alerta(long id);

    void revisarAlerta(long id, String estatus, String justificacion, long idOperador, OffsetDateTime ahora);

    long crearReporte(Long idAlerta, long idOperador, String tipo, String destinatario, String contenidoJson, OffsetDateTime ahora);

    List<Reporte> reportes(int limite);

    Optional<Reporte> reporte(long id);

    void marcarEnviado(long id, String acuse, OffsetDateTime ahora);

    record Operador(long idOperador, String nombre, String correo, String rol, String estatus, String claveHash) {
    }

    record Caso(long idCaso, Long idUsuario, Long idPago, Long idOperador, OffsetDateTime fechaApertura, OffsetDateTime fechaCierre, String tipo,
                String prioridad, String estatus, String descripcion, String resolucion) {
    }

    record Alerta(long idAlerta, long idUsuario, Long idPago, Long idOperador, OffsetDateTime fechaHora, String reglaDisparada, String nivelRiesgo,
                  String estatus, OffsetDateTime fechaRevision, String justificacion) {
    }

    record Reporte(long idReporteReg, Long idAlerta, long idOperador, String tipo, String destinatario, OffsetDateTime fechaGeneracion,
                   OffsetDateTime fechaEnvio, String estatus, String numeroAcuse, String contenido) {
    }
}
