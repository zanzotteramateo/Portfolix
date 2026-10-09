package com.portfolix.api.transaction.dto;

import com.portfolix.api.common.Currency;
import com.portfolix.api.common.Rounding;
import com.portfolix.api.transaction.TransactionTotals;
import com.portfolix.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Tarjetas del historial: total de operaciones, total comprado y total vendido.
 * <ul>
 *   <li>{@code totalBought}/{@code totalSold}: separados por moneda, exactos (no se suman pesos con dólares).
 *   Siempre incluyen ARS y USD, con "0" si no hubo operaciones en esa moneda.</li>
 *   <li>{@code converted}: todo en una sola moneda, convirtiendo cada operación con el dólar de su día.</li>
 * </ul>
 * <pre>{"totalBought": {"ARS": "1488434", "USD": "7240"}, "converted": {"currency": "ARS", "totalBought": "11624434", ...}}</pre>
 */
public record TransactionSummary(
        long totalOperations,
        Map<Currency, BigDecimal> totalBought,
        Map<Currency, BigDecimal> totalSold,
        Converted converted
) {

    /** Totales en una sola moneda, con 2 decimales. */
    public record Converted(Currency currency, BigDecimal totalBought, BigDecimal totalSold) {
    }

    /**
     * @param toDisplayCurrency monto de cada fila convertido a {@code displayCurrency}
     */
    public static TransactionSummary from(List<TransactionTotals> rows, Currency displayCurrency,
                                          Function<TransactionTotals, BigDecimal> toDisplayCurrency) {
        Map<Currency, BigDecimal> bought = zeroForEachCurrency();
        Map<Currency, BigDecimal> sold = zeroForEachCurrency();
        BigDecimal convertedBought = BigDecimal.ZERO;
        BigDecimal convertedSold = BigDecimal.ZERO;
        long operations = 0;

        for (TransactionTotals row : rows) {
            operations += row.count();
            BigDecimal converted = toDisplayCurrency.apply(row);
            if (row.type() == TransactionType.BUY) {
                bought.merge(row.currency(), row.amount(), BigDecimal::add);
                convertedBought = convertedBought.add(converted);
            } else {
                sold.merge(row.currency(), row.amount(), BigDecimal::add);
                convertedSold = convertedSold.add(converted);
            }
        }
        Converted converted = new Converted(displayCurrency,
                Rounding.amount(convertedBought), Rounding.amount(convertedSold));
        return new TransactionSummary(operations, bought, sold, converted);
    }

    private static Map<Currency, BigDecimal> zeroForEachCurrency() {
        Map<Currency, BigDecimal> totals = new EnumMap<>(Currency.class);
        for (Currency currency : Currency.values()) {
            totals.put(currency, BigDecimal.ZERO);
        }
        return totals;
    }
}
