package com.portfolix.api.account.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeRequest(
        @NotBlank(message = "Ingresá el correo nuevo")
        @Email(message = "El correo no es válido")
        @Size(max = 254, message = "El correo puede tener hasta 254 caracteres")
        String newEmail,

        @NotBlank(message = "Ingresá tu contraseña actual")
        String currentPassword
) {
}
