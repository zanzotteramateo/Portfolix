package com.portfolix.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * La contraseña solo se exige no vacía: validar su formato acá le diría a un atacante
 * qué contraseñas no hace falta probar.
 * <p>
 * {@code rememberMe} es {@code Boolean} y no {@code boolean}: Jackson 3 rechaza el JSON si falta
 * un campo primitivo. Si no viene, se toma como {@code false}.
 */
public record LoginRequest(
        @NotBlank(message = "Ingresá tu correo")
        String email,

        @NotBlank(message = "Ingresá tu contraseña")
        String password,

        Boolean rememberMe
) {

    public LoginRequest {
        rememberMe = Boolean.TRUE.equals(rememberMe);
    }
}
