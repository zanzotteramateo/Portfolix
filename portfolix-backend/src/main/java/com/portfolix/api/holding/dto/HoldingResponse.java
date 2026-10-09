package com.portfolix.api.holding.dto;

import com.portfolix.api.asset.AssetType;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una fila de la tabla de activos. Todos los montos y precios están en la moneda pedida.
 *
 * @param averagePrice    precio promedio de compra ({@code null} si ya no se tiene nada)
 * @param investedCapital costo de lo que se tiene hoy (cantidad × precio promedio)
 * @param currentValue    cantidad × precio actual
 * @param pnl             ganancia no realizada: capital actual − capital invertido
 * @param pnlPercent      pnl sobre el capital invertido ({@code null} si no se tiene nada)
 * @param realizedPnl     ganancia de las ventas ya hechas de este activo
 * @param change7dPercent variación del precio en 7 días, en la moneda del activo
 * @param sparkline7d     hasta 28 precios de los últimos 7 días, en la moneda del activo (para el minigráfico).
 *                        Ambos son {@code null} si el historial todavía no se cargó: se pide en segundo plano
 *                        y aparece al volver a pedir la tabla.
 */
public record HoldingResponse(
        String symbol,
        String name,
        AssetType type,
        BigDecimal quantity,
        BigDecimal averagePrice,
        BigDecimal investedCapital,
        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal pnl,
        BigDecimal pnlPercent,
        BigDecimal realizedPnl,
        BigDecimal change7dPercent,
        List<BigDecimal> sparkline7d
) {
}
