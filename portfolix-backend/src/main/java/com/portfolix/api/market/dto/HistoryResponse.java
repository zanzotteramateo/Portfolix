package com.portfolix.api.market.dto;

import com.portfolix.api.common.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Gráfico de tendencia de un activo, en su propia moneda (USD para cripto, ARS para el resto).
 *
 * @param range         "24h" o "7d"
 * @param changePercent variación entre el primer punto (inicio del rango) y el último (precio actual)
 */
public record HistoryResponse(
        String symbol,
        Currency currency,
        String range,
        BigDecimal changePercent,
        List<Point> points
) {

    public record Point(Instant time, BigDecimal price) {
    }
}
