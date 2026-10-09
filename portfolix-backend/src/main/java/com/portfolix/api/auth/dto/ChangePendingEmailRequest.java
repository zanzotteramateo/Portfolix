package com.portfolix.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cambio de mail de una cuenta sin verificar. Mail y contraseña actuales se validan como en el login
 * (solo no vacíos: validar el formato le diría a un atacante qué valores no hace falta probar).
 */
public record ChangePendingEmailRequest(
        @NotBlank(message = "Ingresá tu correo")
        String email,

        @NotBlank(message = "Ingresá tu contraseña")
        String password,

        @NotBlank(message = "Ingresá el correo nuevo")
        @Email(message = "El correo no es válido")
        @Size(max = 254, message = "El correo puede tener hasta 254 caracteres")
        String newEmail
) {
}
