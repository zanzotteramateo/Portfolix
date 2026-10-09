package com.portfolix.api.market;

import com.portfolix.api.common.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;

/**
 * Convierte montos entre ARS y USD con el dólar configurado. Devuelve un factor multiplicador:
 * {@code montoEnDestino = montoEnOrigen × factor}.
 */
@Component
public class CurrencyConverter {

    /** 34 dígitos significativos: 1/1400 no tiene representación decimal exacta. */
    public static final MathContext PRECISION = MathContext.DECIMAL128;

    private final FxRateProvider fxRateProvider;

    public CurrencyConverter(FxRateProvider fxRateProvider) {
        this.fxRateProvider = fxRateProvider;
    }

    /** Factor con la cotización de una fecha (para convertir una operación con el dólar de su día). */
    public BigDecimal factorOn(LocalDate date, Currency from, Currency to) {
        return from == to ? BigDecimal.ONE : factor(from, fxRateProvider.rateOn(date));
    }

    /** Factor con la cotización de hoy (para valorizar la tenencia actual). */
    public BigDecimal currentFactor(Currency from, Currency to) {
        return from == to ? BigDecimal.ONE : factor(from, fxRateProvider.currentRate());
    }

    private static BigDecimal factor(Currency from, BigDecimal arsPerUsd) {
        return from == Currency.USD
                ? arsPerUsd                                          // USD → ARS: se multiplica por la cotización
                : BigDecimal.ONE.divide(arsPerUsd, PRECISION);       // ARS → USD: se divide
    }
}
