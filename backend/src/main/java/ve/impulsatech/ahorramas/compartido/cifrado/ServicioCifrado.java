package ve.impulsatech.ahorramas.compartido.cifrado;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifrado simétrico AES-256-GCM para PII en reposo (R-06) y para la bóveda de secretos simulada,
 * más índice ciego HMAC-SHA256 para unicidad/búsqueda sin descifrar. En producción las llaves
 * vienen de KMS/HSM; aquí de la configuración (solo desarrollo).
 */
@Component
public class ServicioCifrado {

    private static final int IV_BYTES = 12;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKeySpec clavePii;
    private final SecretKeySpec claveBoveda;
    private final SecretKeySpec claveIndice;

    public ServicioCifrado(@Value("${ahorra.cifrado.clave-pii}") String clavePii,
                           @Value("${ahorra.cifrado.clave-boveda}") String claveBoveda,
                           @Value("${ahorra.cifrado.clave-indice}") String claveIndice) {
        this.clavePii = new SecretKeySpec(derivar(clavePii), "AES");
        this.claveBoveda = new SecretKeySpec(derivar(claveBoveda), "AES");
        this.claveIndice = new SecretKeySpec(Base64.getDecoder().decode(claveIndice), "HmacSHA256");
    }

    public String cifrarPii(String texto) {
        return cifrar(texto.getBytes(StandardCharsets.UTF_8), clavePii);
    }

    public String descifrarPii(String cifrado) {
        return new String(descifrar(cifrado, clavePii), StandardCharsets.UTF_8);
    }

    public String cifrarSecreto(String texto) {
        return cifrar(texto.getBytes(StandardCharsets.UTF_8), claveBoveda);
    }

    public String descifrarSecreto(String cifrado) {
        return new String(descifrar(cifrado, claveBoveda), StandardCharsets.UTF_8);
    }

    public byte[] cifrarBytes(byte[] datos) {
        return Base64.getDecoder().decode(cifrar(datos, claveBoveda));
    }

    /** Índice ciego determinista (HMAC) para unicidad de correo/teléfono cifrados. */
    public String indiceCiego(String valorNormalizado) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveIndice);
            return HexFormat.of().formatHex(mac.doFinal(valorNormalizado.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular el índice ciego", e);
        }
    }

    public static String sha256Hex(byte[] datos) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(datos));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String texto) {
        return sha256Hex(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] derivar(String claveBase64) {
        byte[] bruta = Base64.getDecoder().decode(claveBase64);
        try {
            return MessageDigest.getInstance("SHA-256").digest(bruta); // normaliza a 256 bits
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cifrar(byte[] datos, SecretKeySpec clave) {
        try {
            byte[] iv = new byte[IV_BYTES];
            ALEATORIO.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(128, iv));
            byte[] cifrado = c.doFinal(datos);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (Exception e) {
            throw new IllegalStateException("Error cifrando", e);
        }
    }

    private static byte[] descifrar(String base64, SecretKeySpec clave) {
        try {
            byte[] todo = Base64.getDecoder().decode(base64);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(128, todo, 0, IV_BYTES));
            return c.doFinal(todo, IV_BYTES, todo.length - IV_BYTES);
        } catch (Exception e) {
            throw new IllegalStateException("Error descifrando", e);
        }
    }
}
