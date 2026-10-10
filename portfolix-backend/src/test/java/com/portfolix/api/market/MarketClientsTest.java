package com.portfolix.api.market;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/**
 * Cada cliente contra un servidor falso ({@link MockRestServiceServer}) que devuelve respuestas
 * reales recortadas: se verifica la URL que se pide y que el JSON se interpreta bien, sin salir a internet.
 */
class MarketClientsTest {

    private static final MarketProperties PROPERTIES = new MarketProperties(
            MarketProperties.Provider.LIVE,
            FxRateType.BLUE,
            new MarketProperties.Refresh(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(12),
                    Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(6)),
            new MarketProperties.Sources("https://dolarapi.test", "https://argentinadatos.test",
                    "https://coingecko.test", "https://data912.test"),
            new MarketProperties.Fixed(BigDecimal.ONE, Map.of()));

    @Test
    void dolarApi_readsTheCurrentQuote() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://dolarapi.test/v1/dolares/blue")).andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"moneda":"USD","casa":"blue","nombre":"Blue","compra":1540,"venta":1560,
                         "fechaActualizacion":"2026-09-29T19:57:00.000Z"}
                        """, MediaType.APPLICATION_JSON));

        DolarApiClient.Response response = new DolarApiClient(builder, PROPERTIES).current(FxRateType.BLUE);

        assertThat(response.compra()).isEqualByComparingTo("1540");
        assertThat(response.venta()).isEqualByComparingTo("1560");
        assertThat(response.fechaActualizacion()).isEqualTo(Instant.parse("2026-09-29T19:57:00Z"));
        server.verify();
    }

    @Test
    void argentinaDatos_readsTheHistory() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://argentinadatos.test/v1/cotizaciones/dolares/blue"))
                .andRespond(withSuccess("""
                        [{"casa":"blue","compra":4,"venta":4,"fecha":"2011-01-03"},
                         {"casa":"blue","compra":1545,"venta":1565,"fecha":"2026-09-29"}]
                        """, MediaType.APPLICATION_JSON));

        List<ArgentinaDatosClient.Entry> history = new ArgentinaDatosClient(builder, PROPERTIES).history(FxRateType.BLUE);

        assertThat(history).hasSize(2);
        assertThat(history.getLast().fecha()).isEqualTo(LocalDate.of(2026, 9, 29));
        assertThat(history.getLast().venta()).isEqualByComparingTo("1565");
    }

    @Test
    void coinGecko_asksForAllCoinsInOneRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(request -> {
                    assertThat(request.getURI().getPath()).isEqualTo("/simple/price");
                    assertThat(URLDecoder.decode(request.getURI().getRawQuery(), StandardCharsets.UTF_8))
                            .isEqualTo("ids=bitcoin,ethereum&vs_currencies=usd&include_24hr_change=true");
                })
                .andRespond(withSuccess("""
                        {"bitcoin":{"usd":83615.28,"usd_24h_change":-0.5},
                         "ethereum":{"usd":2693.46,"usd_24h_change":1.2}}
                        """, MediaType.APPLICATION_JSON));

        Map<String, CoinGeckoClient.PriceEntry> prices =
                new CoinGeckoClient(builder, PROPERTIES).prices(List.of("bitcoin", "ethereum"));

        assertThat(prices.get("bitcoin").usd()).isEqualByComparingTo("83615.28");
        assertThat(prices.get("bitcoin").usd24hChange()).isEqualByComparingTo("-0.5");
        assertThat(prices.get("ethereum").usd()).isEqualByComparingTo("2693.46");
        server.verify();
    }

    @Test
    void data912_readsStocksAndCedears() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data912.test/live/arg_stocks"))
                .andRespond(withSuccess("""
                        [{"symbol":"GGAL","q_bid":365.0,"px_bid":6000.0,"px_ask":6010.0,"c":6005.0,"pct_change":-4.53}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://data912.test/live/arg_cedears"))
                .andRespond(withSuccess("""
                        [{"symbol":"AAPL","c":27400.0,"pct_change":0.5},{"symbol":"DISN","c":14160.0,"pct_change":-0.42}]
                        """, MediaType.APPLICATION_JSON));

        Data912Client client = new Data912Client(builder, PROPERTIES);

        assertThat(client.stocks().getFirst()).isEqualTo(
                new Data912Client.Quote("GGAL", new BigDecimal("6005.0"), new BigDecimal("-4.53")));
        assertThat(client.cedears()).extracting(Data912Client.Quote::symbol).containsExactly("AAPL", "DISN");
        server.verify();
    }

    @Test
    void coinGecko_readsMarketChartPoints() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://coingecko.test/coins/bitcoin/market_chart?vs_currency=usd&days=7"))
                .andRespond(withSuccess("""
                        {"prices":[[1790708400000,83594.01],[1790712000000,83624.93]]}
                        """, MediaType.APPLICATION_JSON));

        List<List<BigDecimal>> points = new CoinGeckoClient(builder, PROPERTIES).marketChart("bitcoin", 7);

        assertThat(points).hasSize(2);
        assertThat(points.get(0).get(0)).isEqualByComparingTo("1790708400000");
        assertThat(points.get(0).get(1)).isEqualByComparingTo("83594.01");
        assertThat(points.get(1).get(1)).isEqualByComparingTo("83624.93");
    }

    @Test
    void data912_readsTheDailyHistoryOfStocksAndCedears() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data912.test/historical/stocks/GGAL"))
                .andRespond(withSuccess("""
                        [{"date":"2026-09-25","o":6405.0,"h":6470.0,"l":6270.0,"c":6290.0,"v":1997725.0,"dr":-0.0164,"sa":0.5557},
                         {"date":"2026-09-28","o":6250.0,"h":6250.0,"l":5950.0,"c":6005.0,"v":6446689.0,"dr":-0.0453,"sa":0.5557}]
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://data912.test/historical/cedears/AAPL"))
                .andRespond(withSuccess("[{\"date\":\"2026-09-28\",\"c\":27400.0}]", MediaType.APPLICATION_JSON));

        Data912Client client = new Data912Client(builder, PROPERTIES);

        assertThat(client.history(com.portfolix.api.asset.AssetType.STOCK, "GGAL")).containsExactly(
                new Data912Client.Candle(LocalDate.of(2026, 9, 25), new BigDecimal("6290.0")),
                new Data912Client.Candle(LocalDate.of(2026, 9, 28), new BigDecimal("6005.0")));
        assertThat(client.history(com.portfolix.api.asset.AssetType.CEDEAR, "AAPL")).hasSize(1);
        server.verify();
    }

    @Test
    void anApiError_isThrown_soTheCacheKeepsTheLastValue() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://data912.test/live/arg_stocks")).andRespond(withServerError());

        assertThatThrownBy(() -> new Data912Client(builder, PROPERTIES).stocks())
                .isInstanceOf(HttpServerErrorException.class);
    }
}
