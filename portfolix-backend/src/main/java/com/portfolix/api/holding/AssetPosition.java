package com.portfolix.api.holding;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.market.CurrencyConverter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * La posición de un activo valorizada al precio de hoy, todo en la moneda pedida.
 *
 * @param priceUpdatedAt cuándo se obtuvo el precio; {@code null} con precios fijos
 */
record AssetPosition(Asset asset, Position position, BigDecimal currentPrice, Instant priceUpdatedAt) {

    BigDecimal quantity() {
        return position.quantity();
    }

    BigDecimal investedCapital() {
        return position.investedCapital();
    }

    BigDecimal realizedPnl() {
        return position.realizedPnl();
    }

    BigDecimal totalBought() {
        return position.totalBought();
    }

    BigDecimal currentValue() {
        return quantity().multiply(currentPrice);
    }

    /** Se tiene algo hoy (los activos vendidos por completo tienen cantidad 0). */
    boolean isOpen() {
        return quantity().signum() > 0;
    }

    /** Precio promedio de compra; no existe si no se tiene nada. */
    BigDecimal averagePrice() {
        return isOpen() ? investedCapital().divide(quantity(), CurrencyConverter.PRECISION) : null;
    }
}
