package com.portfolix.api.account.dto;

import com.portfolix.api.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;

/** La confirmación de la contraseña nueva ("repetí la contraseña") la valida el front. */
public record ChangePasswordRequest(
        @NotBlank(message = "Ingresá tu contraseña actual")
        String currentPassword,

        @StrongPassword
        String newPassword
) {
}
