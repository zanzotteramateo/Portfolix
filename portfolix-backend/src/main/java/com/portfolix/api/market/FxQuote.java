package com.portfolix.api.market;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Cotización del dólar, en pesos por dólar.
 *
 * @param updatedAt cuándo la publicó la fuente; {@code null} con valores fijos
 */
public record FxQuote(FxRateType type, BigDecimal buy, BigDecimal sell, Instant updatedAt) {

    /** El valor que se usa para convertir: el de venta, que es el que se publica como "el dólar". */
    public BigDecimal rate() {
        return sell;
    }
}
