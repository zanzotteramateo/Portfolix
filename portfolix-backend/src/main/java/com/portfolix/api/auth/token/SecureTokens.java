package com.portfolix.api.auth.token;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Genera los tokens aleatorios (refresh tokens y links de los mails) y el hash con el que se guardan.
 * <p>
 * En la base va solo el hash SHA-256: si se filtra la base, los tokens no se pueden usar.
 * Alcanza con SHA-256 (no hace falta BCrypt como con las contraseñas) porque el token tiene
 * 256 bits aleatorios: no hay forma de adivinarlo probando valores.
 */
final class SecureTokens {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecureTokens() {
    }

    /** 32 bytes aleatorios en Base64 apto para URLs (43 caracteres, sin {@code =}). */
    static String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
