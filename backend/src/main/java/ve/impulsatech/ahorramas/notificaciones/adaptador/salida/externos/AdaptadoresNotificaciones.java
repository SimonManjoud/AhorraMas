package ve.impulsatech.ahorramas.notificaciones.adaptador.salida.externos;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ve.impulsatech.ahorramas.compartido.dominio.Reloj;
import ve.impulsatech.ahorramas.compartido.infraestructura.Jdbc;
import ve.impulsatech.ahorramas.notificaciones.aplicacion.puerto.PuertosNotificaciones.*;

/** Adaptadores de Notificaciones: proveedor HTTP (simulado) y persistencia JDBC. */
public final class AdaptadoresNotificaciones {

    private AdaptadoresNotificaciones() {
    }

    @Component
    static class CanalHttp implements CanalNotificaciones {
        private final RestClient http;

        CanalHttp(@Qualifier("simuladores") RestClient http) {
            this.http = http;
        }

        @Override
        public boolean enviar(String canal, String destino, String titulo, String mensaje, String tipo) {
            try {
                http.post().uri("/notificaciones/{c}", canal)
                        .body(Map.of("destino", destino, "titulo", titulo, "mensaje", mensaje, "tipo", tipo)).retrieve().toBodilessEntity();
                return true;
            } catch (RestClientException e) {
                return false;
            }
        }
    }

    @Component
    static class RepositorioJdbc implements Repositorio {
        private final JdbcClient jdbc;
        private final Reloj reloj;

        RepositorioJdbc(JdbcClient jdbc, Reloj reloj) {
            this.jdbc = jdbc;
            this.reloj = reloj;
        }

        @Override
        public void insertar(long idUsuario, String canal, String tipo, String titulo, String mensaje, String estatus, OffsetDateTime fecha) {
            jdbc.sql("""
                    INSERT INTO notificaciones.notificacion (id_usuario, canal, tipo, titulo, mensaje, fecha_hora_envio, estatus)
                    VALUES (:u, :c, :t, :ti, :m, :f, :e)
                    """).param("u", idUsuario).param("c", canal).param("t", tipo).param("ti", recortar(titulo, 120))
                    .param("m", recortar(mensaje, 500)).param("f", fecha).param("e", estatus).update();
        }

        @Override
        public List<Notificacion> listar(long idUsuario, int limite) {
            return jdbc.sql("SELECT * FROM notificaciones.notificacion WHERE id_usuario = :u ORDER BY fecha_hora_envio DESC, id_notificacion DESC LIMIT :l")
                    .param("u", idUsuario).param("l", limite)
                    .query((rs, i) -> new Notificacion(rs.getLong("id_notificacion"), rs.getString("canal"), rs.getString("tipo"),
                            rs.getString("titulo"), rs.getString("mensaje"), Jdbc.fechaHora(rs, "fecha_hora_envio", reloj.zona()),
                            rs.getString("estatus"), rs.getObject("fecha_lectura") != null))
                    .list();
        }

        @Override
        public boolean marcarLeida(long idUsuario, long id, OffsetDateTime fecha) {
            return jdbc.sql("""
                    UPDATE notificaciones.notificacion SET fecha_lectura = COALESCE(fecha_lectura, :f) WHERE id_notificacion = :id AND id_usuario = :u
                    """).param("f", fecha).param("id", id).param("u", idUsuario).update() > 0;
        }

        private static String recortar(String s, int max) {
            return s == null ? "" : s.length() > max ? s.substring(0, max) : s;
        }
    }
}
