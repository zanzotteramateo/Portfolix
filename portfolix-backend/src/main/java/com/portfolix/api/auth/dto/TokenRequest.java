package com.portfolix.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * El token de un link de mail (verificar el mail, confirmar un cambio de mail):
 * la página del front lo lee de la URL y lo manda acá.
 */
public record TokenRequest(
        @NotBlank(message = "Falta el token del link")
        @Size(max = 100, message = "El link no es válido")
        String token
) {
}
