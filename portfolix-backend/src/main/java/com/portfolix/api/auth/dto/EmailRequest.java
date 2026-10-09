package com.portfolix.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pedidos que solo llevan un mail (ej.: reenviar el link de verificación). */
public record EmailRequest(
        @NotBlank(message = "Ingresá tu correo")
        @Email(message = "El correo no es válido")
        @Size(max = 254, message = "El correo puede tener hasta 254 caracteres")
        String email
) {
}
