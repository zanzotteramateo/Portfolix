package com.portfolix.api.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Los cálculos se hacen con precisión completa y se redondean solo al armar la respuesta.
 */
public final class Rounding {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private Rounding() {
    }

    /** Montos (capital, ganancias): 2 decimales. */
    public static BigDecimal amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /** Precios unitarios: hasta 8 decimales (hay cripto que valen fracciones de centavo). */
    public static BigDecimal unitPrice(BigDecimal value) {
        return value == null ? null : value.setScale(8, RoundingMode.HALF_UP);
    }

    /** {@code part / whole} en porcentaje con 2 decimales; {@code null} si {@code whole} es cero. */
    public static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? null : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }
}
