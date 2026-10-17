package com.portfolix.api.performance.dto;

import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Cómo te fue en un período. Todos los montos están en {@code currency}; los totales son la suma de las
 * filas de {@code assets}.
 *
 * @param from            inicio del período (la primera operación si no se pidió uno)
 * @param startValue      valor de las tenencias al cierre del día anterior a {@code from}
 * @param endValue        valor al cierre de {@code to} (si es hoy, con el precio actual, como el dashboard)
 * @param bought          compras del período, cada una con el dólar de su día
 * @param sold            ventas del período
 * @param pnl             resultado: endValue − startValue − bought + sold
 * @param pnlPercent      rendimiento ponderado por tiempo (Modified Dietz); {@code null} si no hubo capital
 * @param best            el activo que más ganó, entre los que ganaron; {@code null} si ninguno ganó
 * @param worst           el que más perdió, entre los que perdieron; {@code null} si ninguno perdió
 * @param series          valor día por día (semana por semana en rangos de más de 400 días), para el gráfico
 * @param assets          cada activo que se tuvo en algún momento del período (incluye los vendidos), de
 *                        mayor a menor resultado
 * @param pricesUpdatedAt hora del precio actual más viejo usado; {@code null} si {@code to} no es hoy o con
 *                        precios fijos
 */
public record PerformanceResponse(
        Currency currency,
        LocalDate from,
        LocalDate to,
        BigDecimal startValue,
        BigDecimal endValue,
        BigDecimal bought,
        BigDecimal sold,
        BigDecimal pnl,
        BigDecimal pnlPercent,
        AssetPerformance best,
        AssetPerformance worst,
        List<ValuePoint> series,
        List<AssetPerformance> assets,
        Instant pricesUpdatedAt
) {

    /**
     * @param estimatedPrice algún día del período no tenía precio de mercado (ej.: todavía no cotizaba en la
     *                       fuente) y se usó el de la última operación propia de ese activo
     */
    public record AssetPerformance(
            String symbol,
            String name,
            AssetType type,
            BigDecimal startQuantity,
            BigDecimal endQuantity,
            BigDecimal startValue,
            BigDecimal bought,
            BigDecimal sold,
            BigDecimal endValue,
            BigDecimal pnl,
            BigDecimal pnlPercent,
            boolean estimatedPrice
    ) {
    }

    /**
     * @param date        el primer punto es el día anterior a {@code from} (el valor inicial)
     * @param value       valor de las tenencias al cierre de ese día
     * @param netInvested plata puesta: valor inicial + compras − ventas hasta ese día. La distancia con
     *                    {@code value} es lo que se lleva ganado o perdido
     */
    public record ValuePoint(LocalDate date, BigDecimal value, BigDecimal netInvested) {
    }
}
