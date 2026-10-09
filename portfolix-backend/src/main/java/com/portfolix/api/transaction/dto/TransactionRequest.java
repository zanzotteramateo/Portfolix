package com.portfolix.api.transaction.dto;

import com.portfolix.api.common.validation.NotFutureDate;
import com.portfolix.api.transaction.TransactionType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cantidad y precio se aceptan como string ("0.05") o número (0.05).
 * Hasta 20 enteros y 18 decimales, lo que admite la columna NUMERIC(38,18).
 */
public record TransactionRequest(
        @NotNull(message = "Elegí un portafolio")
        Long portfolioId,

        @NotBlank(message = "Elegí un activo")
        String assetSymbol,

        @NotNull(message = "Indicá si es una compra o una venta")
        TransactionType type,

        @NotNull(message = "Ingresá una cantidad")
        @Positive(message = "La cantidad debe ser mayor a 0")
        @Digits(integer = 20, fraction = 18, message = "La cantidad admite hasta 20 enteros y 18 decimales")
        BigDecimal quantity,

        @NotNull(message = "Ingresá un precio")
        @Positive(message = "El precio debe ser mayor a 0")
        @Digits(integer = 20, fraction = 18, message = "El precio admite hasta 20 enteros y 18 decimales")
        BigDecimal price,

        @NotNull(message = "Ingresá la fecha de la operación")
        @NotFutureDate
        LocalDate tradeDate,

        @Size(max = 500, message = "Las notas pueden tener hasta 500 caracteres")
        String notes
) {
}
