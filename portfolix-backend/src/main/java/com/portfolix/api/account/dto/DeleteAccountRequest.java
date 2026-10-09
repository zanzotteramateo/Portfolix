package com.portfolix.api.account.dto;

import jakarta.validation.constraints.NotBlank;

/** El "escribí ELIMINAR" del modal lo valida el front; la API pide la contraseña. */
public record DeleteAccountRequest(
        @NotBlank(message = "Ingresá tu contraseña actual")
        String currentPassword
) {
}
