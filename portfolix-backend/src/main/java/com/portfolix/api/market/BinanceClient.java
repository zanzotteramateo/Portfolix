package com.portfolix.api.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * Binance (API pública, sin clave): precio y variación de 24 h de las cripto contra USDT (≈ 1 USD).
 * <p>
 * Límite conocido: si se le pide un par que Binance no tiene, rechaza el pedido entero.
 * En ese caso se siguen usando los últimos precios conocidos de todas las cripto.
 */
@Component
class BinanceClient {

    private static final ParameterizedTypeReference<List<Ticker>> TICKERS = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<List<Object>>> ROWS = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    BinanceClient(RestClient.Builder builder, MarketProperties properties) {
        this.restClient = builder.baseUrl(properties.sources().binance()).build();
    }

    /**
     * {@code GET /api/v3/ticker/24hr?symbols=["BTCUSDT","ETHUSDT"]} → un ticker por par, en un solo pedido.
     */
    List<Ticker> tickers24h(Collection<String> pairs) {
        String symbols = pairs.stream().map(pair -> "\"" + pair + "\"").collect(joining(",", "[", "]"));
        List<Ticker> tickers = restClient.get()
                // El valor va como variable para que se codifique entero ([, " y , no son válidos en una URL).
                .uri(uri -> uri.path("/api/v3/ticker/24hr").queryParam("symbols", "{symbols}").build(symbols))
                .retrieve()
                .body(TICKERS);
        return tickers == null ? List.of() : tickers;
    }

    /**
     * {@code GET /api/v3/klines?symbol=BTCUSDT&interval=1h&limit=168}: velas de un par, de la más vieja a la más nueva.
     * Cada vela viene como una lista sin nombres: [openTime, open, high, low, close, volume, closeTime, ...].
     * La última es la vela en curso: su cierre es el precio actual.
     */
    List<Kline> klines(String pair, String interval, int limit) {
        List<List<Object>> rows = restClient.get()
                .uri(uri -> uri.path("/api/v3/klines")
                        .queryParam("symbol", pair)
                        .queryParam("interval", interval)
                        .queryParam("limit", limit)
                        .build())
                .retrieve()
                .body(ROWS);
        if (rows == null) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new Kline(
                        Instant.ofEpochMilli(((Number) row.get(0)).longValue()),
                        new BigDecimal(row.get(1).toString()),
                        new BigDecimal(row.get(4).toString()),
                        Instant.ofEpochMilli(((Number) row.get(6)).longValue())))
                .toList();
    }

    record Kline(Instant openTime, BigDecimal open, BigDecimal close, Instant closeTime) {
    }

    /**
     * @param priceChangePercent variación de las últimas 24 h, en porcentaje ("-0.513" = −0,513 %)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ticker(String symbol, BigDecimal lastPrice, BigDecimal priceChangePercent) {
    }
}
