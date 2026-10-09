package com.portfolix.api.auth.dto;

/**
 * Respuesta de login y refresh. El refresh token no va acá: viaja en una cookie HttpOnly.
 *
 * @param expiresIn segundos hasta que vence el access token
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn) {

    public static AuthResponse bearer(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn);
    }
}
