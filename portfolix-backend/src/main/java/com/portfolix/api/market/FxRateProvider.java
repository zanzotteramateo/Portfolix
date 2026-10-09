package com.portfolix.api.market;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cotización del dólar configurado en {@code portfolix.market.fx-type}, en pesos por dólar.
 */
public interface FxRateProvider {

    FxQuote currentQuote();

    /**
     * Cotización de una fecha pasada: sirve para convertir cada operación con el dólar de su día.
     * Si ese día no hubo cotización (fin de semana, feriado), la del último día anterior que tenga.
     */
    BigDecimal rateOn(LocalDate date);

    default BigDecimal currentRate() {
        return currentQuote().rate();
    }
}
