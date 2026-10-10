package com.portfolix.api.market;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * CoinGecko (API pública, sin clave): precio, variación de 24 h e historial de las cripto.
 * En vez de Binance (ver {@code application.yml}, {@code portfolix.market.sources}).
 */
@Component
class CoinGeckoClient {

    private static final ParameterizedTypeReference<Map<String, PriceEntry>> PRICES = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    CoinGeckoClient(RestClient.Builder builder, MarketProperties properties) {
        this.restClient = builder.baseUrl(properties.sources().coinGecko()).build();
    }

    /** {@code GET /simple/price?ids=bitcoin,ethereum&vs_currencies=usd&include_24hr_change=true}: todas en un pedido. */
    Map<String, PriceEntry> prices(Collection<String> coinIds) {
        Map<String, PriceEntry> result = restClient.get()
                .uri(uri -> uri.path("/simple/price")
                        .queryParam("ids", String.join(",", coinIds))
                        .queryParam("vs_currencies", "usd")
                        .queryParam("include_24hr_change", "true")
                        .build())
                .retrieve()
                .body(PRICES);
        return result == null ? Map.of() : result;
    }

    /**
     * {@code GET /coins/{id}/market_chart?vs_currency=usd&days=N}: precios de uno a uno (no hay pedido
     * en lote como en Binance), del más viejo al más nuevo. Cada punto es {@code [epochMillis, precio]}.
     */
    List<List<BigDecimal>> marketChart(String coinId, int days) {
        MarketChartResponse response = restClient.get()
                .uri(uri -> uri.path("/coins/{id}/market_chart")
                        .queryParam("vs_currency", "usd")
                        .queryParam("days", days)
                        .build(coinId))
                .retrieve()
                .body(MarketChartResponse.class);
        return response == null || response.prices() == null ? List.of() : response.prices();
    }

    record PriceEntry(BigDecimal usd, @JsonProperty("usd_24h_change") BigDecimal usd24hChange) {
    }

    record MarketChartResponse(List<List<BigDecimal>> prices) {
    }
}
