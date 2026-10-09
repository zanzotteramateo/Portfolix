package com.portfolix.api.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * ArgentinaDatos (argentinadatos.com): cotización histórica diaria de cada tipo de dólar, desde 2011,
 * toda la serie en un solo pedido. Proyecto de la comunidad, gratuito, sin clave.
 */
@Component
class ArgentinaDatosClient {

    private static final ParameterizedTypeReference<List<Entry>> ENTRIES = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    ArgentinaDatosClient(RestClient.Builder builder, MarketProperties properties) {
        this.restClient = builder.baseUrl(properties.sources().argentinaDatos()).build();
    }

    /** {@code GET /v1/cotizaciones/dolares/{casa}} → [{"compra": 4, "venta": 4, "fecha": "2011-01-03"}, ...] */
    List<Entry> history(FxRateType type) {
        List<Entry> entries = restClient.get()
                .uri("/v1/cotizaciones/dolares/{casa}", type.apiName())
                .retrieve()
                .body(ENTRIES);
        return entries == null ? List.of() : entries;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(BigDecimal compra, BigDecimal venta, LocalDate fecha) {
    }
}
