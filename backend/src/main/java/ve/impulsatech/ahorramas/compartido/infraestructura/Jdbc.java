package ve.impulsatech.ahorramas.compartido.infraestructura;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Ayudas de mapeo JDBC compartidas por los adaptadores de persistencia. */
public final class Jdbc {

    private Jdbc() {
    }

    /** Lee un TIMESTAMPTZ y lo expresa en la zona del negocio (America/Caracas). */
    public static OffsetDateTime fechaHora(ResultSet rs, String columna, ZoneId zona) throws SQLException {
        OffsetDateTime v = rs.getObject(columna, OffsetDateTime.class);
        return v == null ? null : v.atZoneSameInstant(zona).toOffsetDateTime();
    }

    public static LocalDate fecha(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, LocalDate.class);
    }

    public static Long largoNulable(ResultSet rs, String columna) throws SQLException {
        long v = rs.getLong(columna);
        return rs.wasNull() ? null : v;
    }

    public static Integer enteroNulable(ResultSet rs, String columna) throws SQLException {
        int v = rs.getInt(columna);
        return rs.wasNull() ? null : v;
    }

    public static Boolean booleanoNulable(ResultSet rs, String columna) throws SQLException {
        boolean v = rs.getBoolean(columna);
        return rs.wasNull() ? null : v;
    }
}
