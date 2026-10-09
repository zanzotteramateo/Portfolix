package com.portfolix.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Continuar con Google". {@code rememberMe} funciona igual que en el login y, si no viene, es {@code false}.
 *
 * @param idToken el {@code credential} que devuelve el botón de Google: un JWT firmado por Google
 */
public record GoogleLoginRequest(
        @NotBlank(message = "Falta el token de Google")
        @Size(max = 4096, message = "El token de Google no es válido")
        String idToken,

        Boolean rememberMe
) {

    public GoogleLoginRequest {
        rememberMe = Boolean.TRUE.equals(rememberMe);
    }
}
