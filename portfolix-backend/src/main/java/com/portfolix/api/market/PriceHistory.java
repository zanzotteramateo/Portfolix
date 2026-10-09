package com.portfolix.api.market;

import com.portfolix.api.common.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Precios de un activo en un rango, en su propia moneda, del más viejo al más nuevo.
 * El primer punto es el precio al inicio del rango y el último el actual.
 *
 * @param changePercent variación entre el primer y el último punto; {@code null} si hay menos de dos
 */
public record PriceHistory(String symbol, Currency currency, HistoryRange range, BigDecimal changePercent,
                           List<PricePoint> points) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public static PriceHistory of(String symbol, Currency currency, HistoryRange range, List<PricePoint> points) {
        BigDecimal change = null;
        if (points.size() >= 2 && points.getFirst().price().signum() > 0) {
            BigDecimal first = points.getFirst().price();
            change = points.getLast().price().subtract(first).multiply(HUNDRED).divide(first, 2, RoundingMode.HALF_UP);
        }
        return new PriceHistory(symbol, currency, range, change, List.copyOf(points));
    }

    /**
     * Hasta {@code maxPoints} precios repartidos a lo largo del rango (siempre incluye el primero y el último).
     * Para un minigráfico de tabla no hacen falta las 168 velas de una semana.
     */
    public List<BigDecimal> sparkline(int maxPoints) {
        if (points.size() <= maxPoints) {
            return points.stream().map(PricePoint::price).toList();
        }
        List<BigDecimal> sampled = new ArrayList<>(maxPoints);
        double step = (points.size() - 1) / (double) (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            sampled.add(points.get((int) Math.round(i * step)).price());
        }
        return sampled;
    }
}
