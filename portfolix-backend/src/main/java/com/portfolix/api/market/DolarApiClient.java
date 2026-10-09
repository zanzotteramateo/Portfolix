package com.portfolix.api.market;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DolarApi (dolarapi.com): cotización actual de cada tipo de dólar. Gratuita, sin clave.
 */
@Component
class DolarApiClient {

    private final RestClient restClient;

    DolarApiClient(RestClient.Builder builder, MarketProperties properties) {
        this.restClient = builder.baseUrl(properties.sources().dolarApi()).build();
    }

    /** {@code GET /v1/dolares/{casa}} → {"compra": 1540, "venta": 1560, "fechaActualizacion": "..."} */
    Response current(FxRateType type) {
        Response response = restClient.get()
                .uri("/v1/dolares/{casa}", type.apiName())
                .retrieve()
                .body(Response.class);
        if (response == null || response.venta() == null) {
            throw new IllegalStateException("DolarApi devolvió una respuesta sin cotización");
        }
        return response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(BigDecimal compra, BigDecimal venta, Instant fechaActualizacion) {
    }
}
