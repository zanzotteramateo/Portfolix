package com.portfolix.api.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body para crear (POST) y renombrar (PATCH) un portafolio: el nombre es lo único editable.
 */
public record PortfolioRequest(
        @NotBlank(message = "Ingresá un nombre para el portafolio")
        @Size(max = 50, message = "El nombre puede tener hasta 50 caracteres")
        String name
) {
}
