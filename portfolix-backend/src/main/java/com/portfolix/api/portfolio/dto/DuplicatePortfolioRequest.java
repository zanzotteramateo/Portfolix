package com.portfolix.api.portfolio.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body opcional de "Duplicar como nuevo". Sin nombre, la copia se llama "Nombre (copia)".
 * Si viene, tiene que tener algo además de espacios.
 */
public record DuplicatePortfolioRequest(
        @Pattern(regexp = ".*\\S.*", message = "Ingresá un nombre para el portafolio")
        @Size(max = 50, message = "El nombre puede tener hasta 50 caracteres")
        String name
) {
}
