package com.portfolix.api.auth.dto;

import com.portfolix.api.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** El token viene del link del mail; la página del front lo lee de la URL y lo manda junto con la contraseña nueva. */
public record ResetPasswordRequest(
        @NotBlank(message = "Falta el token del link")
        @Size(max = 100, message = "El link no es válido")
        String token,

        @StrongPassword
        String newPassword
) {
}
