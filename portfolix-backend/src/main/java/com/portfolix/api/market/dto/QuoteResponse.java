package com.portfolix.api.market.dto;

import com.portfolix.api.common.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Precio actual de un activo, convertido a {@code currency}.
 *
 * @param change24hPercent variación en la moneda del activo: la del día para acciones y CEDEARs,
 *                         la de las últimas 24 h para cripto; {@code null} si la fuente no la da
 * @param updatedAt        cuándo se obtuvo el precio; {@code null} con precios fijos
 */
public record QuoteResponse(
        String symbol,
        Currency currency,
        BigDecimal price,
        BigDecimal change24hPercent,
        Instant updatedAt
) {
}
