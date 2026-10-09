package com.portfolix.api.market;

import java.math.BigDecimal;
import java.time.Instant;

/** Un punto de un gráfico de precio. */
public record PricePoint(Instant time, BigDecimal price) {
}
