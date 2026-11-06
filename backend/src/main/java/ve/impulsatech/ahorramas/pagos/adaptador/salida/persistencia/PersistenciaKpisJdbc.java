package ve.impulsatech.ahorramas.pagos.adaptador.salida.persistencia;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;
import ve.impulsatech.ahorramas.pagos.aplicacion.puerto.salida.PuertosPagos.Metricas;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis.Mensaje;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis.Pago;
import ve.impulsatech.ahorramas.pagos.dominio.CalculadoraKpis.Transicion;
import ve.impulsatech.ahorramas.pagos.dominio.EstadoPago;

/** Lecturas de solo lectura del ledger de pagos para el tablero de KPIs (todas las tablas son inmutables por trigger). */
@Repository
class PersistenciaKpisJdbc implements Metricas {

    private final JdbcClient jdbc;
    private final Reloj reloj;

    PersistenciaKpisJdbc(JdbcClient jdbc, Reloj reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    @Override
    public List<Pago> pagosCreadosEntre(OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                SELECT id_pago, estado_actual, monto_cts, comision_cts, fecha_hora_creacion, fecha_hora_captura, motivo_rechazo, referencia_suiche
                  FROM pagos.pago_tdc WHERE fecha_hora_creacion >= :d AND fecha_hora_creacion < :h ORDER BY id_pago
                """).param("d", desde).param("h", hasta)
                .query((rs, i) -> new Pago(rs.getLong("id_pago"), EstadoPago.valueOf(rs.getString("estado_actual")), rs.getLong("monto_cts"),
                        rs.getLong("comision_cts"), Jdbc.fechaHora(rs, "fecha_hora_creacion", reloj.zona()),
                        Jdbc.fechaHora(rs, "fecha_hora_captura", reloj.zona()), rs.getString("motivo_rechazo"), rs.getString("referencia_suiche")))
                .list();
    }

    @Override
    public List<Mensaje> mensajesDePagosCreadosEntre(OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                SELECT m.id_pago, m.tipo, m.fecha_hora_envio, m.fecha_hora_respuesta, m.resultado, m.codigo_respuesta
                  FROM pagos.mensaje_suiche m JOIN pagos.pago_tdc p ON p.id_pago = m.id_pago
                 WHERE p.fecha_hora_creacion >= :d AND p.fecha_hora_creacion < :h ORDER BY m.id_mensaje
                """).param("d", desde).param("h", hasta)
                .query((rs, i) -> new Mensaje(rs.getLong("id_pago"), rs.getString("tipo"), Jdbc.fechaHora(rs, "fecha_hora_envio", reloj.zona()),
                        Jdbc.fechaHora(rs, "fecha_hora_respuesta", reloj.zona()), rs.getString("resultado"), rs.getString("codigo_respuesta")))
                .list();
    }

    @Override
    public List<Transicion> transicionesDePagosCreadosEntre(OffsetDateTime desde, OffsetDateTime hasta) {
        return jdbc.sql("""
                SELECT h.id_pago, h.estado_anterior, h.estado_nuevo, h.fecha_hora, h.actor
                  FROM pagos.historial_estado_pago h JOIN pagos.pago_tdc p ON p.id_pago = h.id_pago
                 WHERE p.fecha_hora_creacion >= :d AND p.fecha_hora_creacion < :h ORDER BY h.id_pago, h.secuencia
                """).param("d", desde).param("h", hasta)
                .query((rs, i) -> new Transicion(rs.getLong("id_pago"),
                        rs.getString("estado_anterior") == null ? null : EstadoPago.valueOf(rs.getString("estado_anterior")),
                        EstadoPago.valueOf(rs.getString("estado_nuevo")), Jdbc.fechaHora(rs, "fecha_hora", reloj.zona()), rs.getString("actor")))
                .list();
    }
}
