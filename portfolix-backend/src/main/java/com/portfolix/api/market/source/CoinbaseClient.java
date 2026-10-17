package com.portfolix.api.market.source;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Coinbase Exchange (api.exchange.coinbase.com): velas diarias de las cripto, con años de historia.
 * API pública, sin clave. Solo se usa para el historial largo del análisis: CoinGecko gratis da solo
 * los últimos 365 días.
 */
@Component
class CoinbaseClient {

    /** Velas por pedido como máximo: con más, Coinbase responde 400 ("Count of aggregations requested exceeds 300"). */
    static final int MAX_CANDLES = 300;

    /** Coinbase rechaza los pedidos sin este header (400 "User-Agent header is required"). */
    private static final String USER_AGENT = "Portfolix";
    private static final int ONE_DAY_SECONDS = 86_400;
    private static final ParameterizedTypeReference<List<List<BigDecimal>>> CANDLES = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    CoinbaseClient(RestClient.Builder builder, MarketProperties properties) {
        this.restClient = builder.baseUrl(properties.sources().coinbase())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .build();
    }

    /**
     * {@code GET /products/{SÍMBOLO}-USD/candles?granularity=86400&start=...&end=...}: una vela por día UTC
     * entre dos fechas (las dos incluidas), de la más nueva a la más vieja. Cada vela es
     * {@code [inicio en segundos, mínimo, máximo, apertura, cierre, volumen]}.
     *
     * @return vacío si Coinbase no tiene esa cripto (404) o si todavía no cotizaba en esas fechas
     */
    List<DailyClose> dailyCloses(String symbol, LocalDate start, LocalDate end) {
        List<List<BigDecimal>> candles;
        try {
            candles = restClient.get()
                    .uri(uri -> uri.path("/products/{product}/candles")
                            .queryParam("granularity", ONE_DAY_SECONDS)
                            .queryParam("start", start.atStartOfDay(ZoneOffset.UTC).toInstant())
                            .queryParam("end", end.atStartOfDay(ZoneOffset.UTC).toInstant())
                            .build(symbol + "-USD"))
                    .retrieve()
                    .body(CANDLES);
        } catch (HttpClientErrorException.NotFound ex) {
            return List.of();
        }
        if (candles == null) {
            return List.of();
        }
        return candles.stream()
                .map(candle -> new DailyClose(
                        LocalDate.ofInstant(Instant.ofEpochSecond(candle.get(0).longValueExact()), ZoneOffset.UTC),
                        candle.get(4)))
                .toList();
    }

    record DailyClose(LocalDate date, BigDecimal close) {
    }
}
