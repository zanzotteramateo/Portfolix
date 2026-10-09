package com.portfolix.api.holding;

import java.math.BigDecimal;

/**
 * Resultado de procesar las transacciones de un activo, con los montos en la moneda pedida.
 *
 * @param quantity        cantidad que se tiene hoy
 * @param investedCapital costo de lo que se tiene hoy (cantidad × precio promedio)
 * @param realizedPnl     ganancia (o pérdida) de las ventas ya hechas
 * @param totalBought     todo lo que se pagó en compras, incluido lo que ya se vendió
 */
record Position(BigDecimal quantity, BigDecimal investedCapital, BigDecimal realizedPnl, BigDecimal totalBought) {

    static final Position EMPTY = new Position(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

    Position plus(Position other) {
        return new Position(
                quantity.add(other.quantity),
                investedCapital.add(other.investedCapital),
                realizedPnl.add(other.realizedPnl),
                totalBought.add(other.totalBought));
    }
}
