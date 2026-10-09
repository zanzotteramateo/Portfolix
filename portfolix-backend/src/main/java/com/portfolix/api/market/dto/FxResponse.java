package com.portfolix.api.market.dto;

import com.portfolix.api.market.FxRateType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dólar que usa la app, en pesos por dólar.
 *
 * @param rate      el valor con el que se convierte (el de venta)
 * @param updatedAt cuándo lo publicó la fuente; {@code null} con valores fijos
 */
public record FxResponse(FxRateType type, BigDecimal buy, BigDecimal sell, BigDecimal rate, Instant updatedAt) {
}
