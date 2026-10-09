package com.portfolix.api.auth;

import com.portfolix.api.auth.dto.AuthResponse;
import com.portfolix.api.auth.token.IssuedRefreshToken;

/**
 * Resultado de login/refresh: lo que va en el body y el refresh token que va en la cookie.
 */
public record AuthResult(AuthResponse body, IssuedRefreshToken refreshToken) {
}
