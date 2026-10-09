package com.portfolix.api.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cotización de un activo, en su propia moneda.
 *
 * @param change24hPercent variación en porcentaje: la del día contra el cierre anterior para acciones y
 *                         CEDEARs, la de las últimas 24 h para cripto; {@code null} si la fuente no la da
 * @param updatedAt        cuándo se obtuvo; {@code null} con precios fijos
 */
public record PriceQuote(BigDecimal price, BigDecimal change24hPercent, Instant updatedAt) {
}
