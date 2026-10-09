package com.portfolix.api.auth.dto;

import com.portfolix.api.common.validation.StrongPassword;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Ingresá tu nombre")
        @Size(max = 100, message = "El nombre puede tener hasta 100 caracteres")
        String fullName,

        @NotBlank(message = "Ingresá tu correo")
        @Email(message = "El correo no es válido")
        @Size(max = 254, message = "El correo puede tener hasta 254 caracteres")
        String email,

        @StrongPassword
        String password,

        // Boolean (no boolean): si falta, Jackson 3 rechazaría el JSON entero en vez de dar este mensaje.
        @NotNull(message = "Tenés que aceptar los términos y condiciones")
        @AssertTrue(message = "Tenés que aceptar los términos y condiciones")
        Boolean acceptedTerms
) {
}
