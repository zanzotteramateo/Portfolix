package com.portfolix.api.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Cuánto se puede vender de un activo en una fecha sin que la tenencia quede negativa
 * en ningún momento, ni ese día ni después.
 * <p>
 * Ejemplo: compra 10 el 01/09, venta 8 el 20/09. Tenencia hoy: 2. Pero una venta con fecha
 * 05/09 solo puede ser de 2 (no de 10): si fuera de 10, el 20/09 la tenencia quedaría en −8.
 * <p>
 * Todas las listas van en orden cronológico: por fecha y, dentro del mismo día, por orden de alta
 * (el mismo con el que se calculan las posiciones).
 */
final class SellableQuantity {

    private SellableQuantity() {
    }

    /**
     * Para una venta nueva: se ubica después de todas las transacciones de su mismo día o anteriores.
     *
     * @param transactions las transacciones de un activo en un portafolio, ordenadas por fecha y alta
     */
    static BigDecimal at(List<Transaction> transactions, LocalDate saleDate) {
        int position = (int) transactions.stream().filter(tx -> !tx.getTradeDate().isAfter(saleDate)).count();
        return at(transactions, position);
    }

    /**
     * Para una venta ubicada en una posición del historial (0 = antes de todas). Al editar una venta,
     * conserva su lugar dentro del día: el de su orden de alta, no el final del día como una nueva.
     * El disponible es la tenencia mínima desde ese punto en adelante.
     *
     * @param others las demás transacciones del activo en el portafolio (sin la venta), ordenadas por fecha y alta
     */
    static BigDecimal at(List<Transaction> others, int position) {
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal minimumFromSale = null; // null = todavía no llegamos a la posición de la venta

        for (int i = 0; i < others.size(); i++) {
            if (i == position) {
                minimumFromSale = balance; // tenencia en el momento de la venta
            }
            balance = apply(balance, others.get(i));
            if (minimumFromSale != null) {
                minimumFromSale = minimumFromSale.min(balance);
            }
        }
        BigDecimal available = minimumFromSale == null ? balance : minimumFromSale;
        return available.max(BigDecimal.ZERO);
    }

    /** Tenencia actual: compras − ventas, sin importar fechas. */
    static BigDecimal current(List<Transaction> transactions) {
        return transactions.stream()
                .map(tx -> tx.getType() == TransactionType.BUY ? tx.getQuantity() : tx.getQuantity().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * La primera venta con la que la tenencia queda negativa, o vacío si el historial es válido.
     * Sirve para saber si borrar o editar una transacción deja descubierta alguna venta.
     *
     * @param transactions el historial como quedaría, ordenado por fecha y alta
     */
    static Optional<Transaction> firstUncoveredSale(List<Transaction> transactions) {
        BigDecimal balance = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            balance = apply(balance, tx);
            if (balance.signum() < 0) {
                return Optional.of(tx); // solo una venta puede bajar la tenencia
            }
        }
        return Optional.empty();
    }

    private static BigDecimal apply(BigDecimal balance, Transaction tx) {
        return tx.getType() == TransactionType.BUY
                ? balance.add(tx.getQuantity())
                : balance.subtract(tx.getQuantity());
    }
}
