package com.portfolix.api.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.portfolix.api.asset.AssetType;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Data912 (data912.com): precios de BYMA en pesos, todo el panel en un solo pedido.
 * Proyecto de la comunidad, gratuito, sin clave.
 */
@Component
class Data912Client {

    private static final ParameterizedTypeReference<List<Quote>> QUOTES = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<Candle>> CANDLES = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    Data912Client(RestClient.Builder builder, MarketProperties properties) {
        this.restClient = builder.baseUrl(properties.sources().data912()).build();
    }

    /** {@code GET /live/arg_stocks}: acciones argentinas. */
    List<Quote> stocks() {
        return get("/live/arg_stocks");
    }

    /** {@code GET /live/arg_cedears}: CEDEARs (en pesos; las variantes con sufijo C y D cotizan en dólares). */
    List<Quote> cedears() {
        return get("/live/arg_cedears");
    }

    /**
     * {@code GET /historical/{stocks|cedears}/{ticker}}: velas diarias desde que cotiza (miles de días,
     * ~600 KB; la API no permite pedir un rango), de la más vieja a la más nueva.
     */
    List<Candle> history(AssetType type, String symbol) {
        String market = type == AssetType.CEDEAR ? "cedears" : "stocks";
        List<Candle> candles = restClient.get()
                .uri("/historical/{market}/{ticker}", market, symbol)
                .retrieve()
                .body(CANDLES);
        return candles == null ? List.of() : candles;
    }

    private List<Quote> get(String path) {
        List<Quote> quotes = restClient.get().uri(path).retrieve().body(QUOTES);
        return quotes == null ? List.of() : quotes;
    }

    /**
     * @param close precio de cierre del día ("c" en la API)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candle(LocalDate date, @JsonProperty("c") BigDecimal close) {
    }

    /**
     * @param price       último precio operado ("c" en la API)
     * @param pctChange   variación del día contra el cierre anterior, en porcentaje
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Quote(String symbol, @JsonProperty("c") BigDecimal price, @JsonProperty("pct_change") BigDecimal pctChange) {
    }
}
