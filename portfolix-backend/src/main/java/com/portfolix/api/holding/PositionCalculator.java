package com.portfolix.api.holding;

import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * Calcula la posición de un activo en un portafolio con el método de precio promedio ponderado.
 * Es una clase pura (sin base ni Spring), así se prueba con casos hechos a mano.
 * <pre>
 * Compra 10 a $100  → tenés 10, invertido $1.000, promedio $100
 * Compra 10 a $200  → tenés 20, invertido $3.000, promedio $150
 * Venta   5 a $300  → tenés 15, invertido $2.250, promedio $150 (vender no cambia el promedio)
 *                     ganancia realizada = 5 × (300 − 150) = $750
 * </pre>
 */
final class PositionCalculator {

    private PositionCalculator() {
    }

    /**
     * @param transactions      transacciones de un activo en un portafolio, en orden cronológico
     * @param toDisplayCurrency factor para pasar el monto de cada operación a la moneda pedida,
     *                          con la cotización del día de la operación
     */
    static Position calculate(List<Transaction> transactions, Function<LocalDate, BigDecimal> toDisplayCurrency) {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal bought = BigDecimal.ZERO;

        for (Transaction tx : transactions) {
            BigDecimal amount = tx.getQuantity().multiply(tx.getPrice())
                    .multiply(toDisplayCurrency.apply(tx.getTradeDate()));

            if (tx.getType() == TransactionType.BUY) {
                quantity = quantity.add(tx.getQuantity());
                invested = invested.add(amount);
                bought = bought.add(amount);
                continue;
            }

            if (tx.getQuantity().compareTo(quantity) > 0) {
                // No debería pasar: el alta de transacciones no deja vender más de lo que se tiene.
                throw new IllegalStateException("La transacción %d vende más de la tenencia".formatted(tx.getId()));
            }
            // Costo de lo vendido = invertido × (vendido / tenencia). Es lo mismo que promedio × vendido,
            // pero sin redondear el promedio en el medio.
            BigDecimal costOfSold = invested.multiply(tx.getQuantity())
                    .divide(quantity, CurrencyConverter.PRECISION);
            realized = realized.add(amount.subtract(costOfSold));
            quantity = quantity.subtract(tx.getQuantity());
            // Si se vendió todo, el invertido queda en cero exacto (sin restos de la división).
            invested = quantity.signum() == 0 ? BigDecimal.ZERO : invested.subtract(costOfSold);
        }
        return new Position(quantity, invested, realized, bought);
    }
}
