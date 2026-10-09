package com.portfolix.api.market;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

/**
 * Configuración de datos de mercado ({@code portfolix.market}).
 *
 * @param provider de dónde salen precios y dólar: APIs reales o valores fijos
 * @param fxType   qué dólar se usa para convertir entre ARS y USD
 * @param refresh  cada cuánto se piden valores nuevos a las APIs
 * @param sources  URLs base de las APIs
 * @param fixed    precios y dólar fijos (market/fixed-prices.yml)
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.market")
public record MarketProperties(
        @NotNull Provider provider,
        @NotNull FxRateType fxType,
        @NotNull @Valid Refresh refresh,
        @NotNull @Valid Sources sources,
        @NotNull @Valid Fixed fixed
) {

    public enum Provider {
        LIVE,
        FIXED
    }

    public record Refresh(
            @NotNull Duration prices,
            @NotNull Duration fx,
            @NotNull Duration fxHistory,
            @NotNull Duration cryptoDayHistory,
            @NotNull Duration cryptoWeekHistory,
            @NotNull Duration dailyCloses
    ) {
    }

    public record Sources(
            @NotBlank String dolarApi,
            @NotBlank String argentinaDatos,
            @NotBlank String binance,
            @NotBlank String data912
    ) {
    }

    /**
     * @param usdRate pesos por dólar
     * @param prices  precio de cada activo en su moneda, por símbolo
     */
    public record Fixed(
            @NotNull @Positive BigDecimal usdRate,
            @NotNull Map<String, BigDecimal> prices
    ) {
    }
}
