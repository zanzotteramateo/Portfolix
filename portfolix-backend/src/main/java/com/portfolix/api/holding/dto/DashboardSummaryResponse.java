package com.portfolix.api.holding.dto;

import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.market.FxRateType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Cabecera del dashboard y gráfico de distribución. Todos los montos están en {@code currency}.
 *
 * @param currentValue    capital actual (suma de la columna "capital actual" de la tabla)
 * @param investedCapital costo de lo que se tiene hoy
 * @param totalPnl        ganancia total: no realizada (actual − invertido) + realizada (ventas)
 * @param totalPnlPercent totalPnl sobre todo lo comprado alguna vez; {@code null} si no hay compras
 * @param holdingsCount   cantidad de activos que se tienen hoy
 * @param distribution    siempre los tres tipos, aunque alguno esté en cero
 * @param fx              dólar usado, para mostrar el valor en la otra moneda
 * @param pricesUpdatedAt hora del precio más viejo usado; {@code null} con precios fijos o sin posiciones
 */
public record DashboardSummaryResponse(
        Currency currency,
        BigDecimal currentValue,
        BigDecimal investedCapital,
        BigDecimal totalPnl,
        BigDecimal totalPnlPercent,
        BigDecimal unrealizedPnl,
        BigDecimal realizedPnl,
        int holdingsCount,
        List<TypeAllocation> distribution,
        FxInfo fx,
        Instant pricesUpdatedAt
) {

    /**
     * @param percent con 1 decimal, como en el diseño, ajustado para que los tres sumen 100;
     *                {@code null} si no hay capital
     */
    public record TypeAllocation(AssetType type, BigDecimal value, BigDecimal percent) {
    }

    /**
     * @param rate pesos por dólar
     */
    public record FxInfo(FxRateType type, BigDecimal rate) {
    }
}
