package ve.impulsatech.ahorramas.simuladores.comun;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Random;

/** Utilidades de hash para generar datos deterministas (misma entrada ⇒ mismos datos). */
public final class Semilla {

    private Semilla() {
    }

    public static String sha256Hex(String texto) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Semilla de 64 bits a partir de las partes unidas por '|'. */
    public static long de(Object... partes) {
        return Long.parseUnsignedLong(sha256Hex(unir(partes)).substring(0, 16), 16);
    }

    /** {@link Random} determinista (su algoritmo está especificado por la JDK). */
    public static Random random(Object... partes) {
        return new Random(de(partes));
    }

    /** Primeros {@code largo} caracteres hexadecimales del SHA-256 de las partes. */
    public static String hex(int largo, Object... partes) {
        return sha256Hex(unir(partes)).substring(0, largo);
    }

    private static String unir(Object... partes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(partes[i]);
        }
        return sb.toString();
    }
}
