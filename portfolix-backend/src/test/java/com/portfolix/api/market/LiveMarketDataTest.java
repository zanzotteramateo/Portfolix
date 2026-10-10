package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caché y política de fallas de LiveMarketData, con las APIs simuladas.
 * El reloj de Caffeine es falso (se adelanta a mano) y el refresco corre en el mismo hilo:
 * así el paso del tiempo es instantáneo y los resultados no dependen del azar.
 */
class LiveMarketDataTest {

    private static final Instant NOW = Instant.parse("2026-09-29T15:00:00Z");
    private static final Duration PRICES_REFRESH = Duration.ofMinutes(5);

    private final DolarApiClient dolarApi = mock(DolarApiClient.class);
    private final ArgentinaDatosClient argentinaDatos = mock(ArgentinaDatosClient.class);
    private final CoinGeckoClient coinGecko = mock(CoinGeckoClient.class);
    private final Data912Client data912 = mock(Data912Client.class);
    private final AssetService assetService = mock(AssetService.class);
    private final AtomicLong nanos = new AtomicLong();

    private final Asset ggal = new Asset("GGAL", "Grupo Financiero Galicia", AssetType.STOCK, Currency.ARS);
    private final Asset ypfd = new Asset("YPFD", "YPF S.A.", AssetType.STOCK, Currency.ARS);
    private final Asset aapl = new Asset("AAPL", "Apple Inc.", AssetType.CEDEAR, Currency.ARS);
    private final Asset btc = new Asset("BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD);
    private final Asset usdt = new Asset("USDT", "Tether", AssetType.CRYPTO, Currency.USD);

    private LiveMarketData market;

    @BeforeEach
    void setUp() {
        MarketProperties properties = new MarketProperties(
                MarketProperties.Provider.LIVE,
                FxRateType.BLUE,
                new MarketProperties.Refresh(PRICES_REFRESH, Duration.ofMinutes(15), Duration.ofHours(12),
                        Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(6)),
                new MarketProperties.Sources("https://a.test", "https://b.test", "https://c.test", "https://d.test"),
                new MarketProperties.Fixed(BigDecimal.ONE, Map.of()));
        market = new LiveMarketData(dolarApi, argentinaDatos, coinGecko, data912, assetService, properties,
                Clock.fixed(NOW, ZoneOffset.UTC), nanos::get, Runnable::run);
        when(assetService.symbolsOfType(AssetType.CRYPTO)).thenReturn(List.of("BTC", "ETH", "USDT"));
    }

    @Test
    void stocksAndCedearsComeFromData912_andCryptoFromCoinGecko() {
        when(data912.stocks()).thenReturn(List.of(stock("GGAL", "6005", "-4.53")));
        when(data912.cedears()).thenReturn(List.of(stock("AAPL", "27400", "0.5")));
        when(coinGecko.prices(anyCollection())).thenReturn(Map.of(
                "bitcoin", new CoinGeckoClient.PriceEntry(new BigDecimal("83615.28"), new BigDecimal("-0.5")),
                "ethereum", new CoinGeckoClient.PriceEntry(new BigDecimal("2693.46"), new BigDecimal("1.2"))));

        assertThat(market.quote(ggal)).isEqualTo(new PriceQuote(new BigDecimal("6005"), new BigDecimal("-4.53"), NOW));
        assertThat(market.quote(aapl).price()).isEqualByComparingTo("27400");
        assertThat(market.quote(btc).price()).isEqualByComparingTo("83615.28");
        // USDT se fija en 1 sin pedírselo a CoinGecko (si no, un "stablecoin" se movería unos centavos).
        assertThat(market.quote(usdt).price()).isEqualByComparingTo("1");
        verify(coinGecko).prices(argThat(ids -> ids.containsAll(List.of("bitcoin", "ethereum")) && !ids.contains("tether")));
    }

    @Test
    void pricesAreCached_untilTheRefreshInterval() {
        when(data912.stocks())
                .thenReturn(List.of(stock("GGAL", "6005", "0")))
                .thenReturn(List.of(stock("GGAL", "6100", "0")));

        market.quote(ggal);
        market.quote(ggal);
        verify(data912, times(1)).stocks(); // la segunda vez sale del caché

        advance(PRICES_REFRESH.plusSeconds(1));
        // Pasado el intervalo, el pedido dispara el refresco. En producción corre en otro hilo y ese pedido
        // recibe el valor viejo sin esperar; acá corre en el mismo hilo (Runnable::run), así que ya ve el nuevo.
        assertThat(market.quote(ggal).price()).isEqualByComparingTo("6100");
        assertThat(market.quote(ggal).price()).isEqualByComparingTo("6100");
        verify(data912, times(2)).stocks();
    }

    @Test
    void whenTheApiFails_theLastKnownPriceIsKept() {
        when(data912.stocks())
                .thenReturn(List.of(stock("GGAL", "6005", "0")))
                .thenThrow(new ResourceAccessException("timeout"));
        market.quote(ggal);

        advance(PRICES_REFRESH.plusSeconds(1));
        market.quote(ggal); // dispara el refresco, que falla

        PriceQuote quote = market.quote(ggal);
        assertThat(quote.price()).isEqualByComparingTo("6005");
        assertThat(quote.updatedAt()).isEqualTo(NOW); // de cuándo es: el front puede mostrar "hace X"
    }

    @Test
    void aSymbolMissingInANewResponse_keepsItsLastPrice() {
        when(data912.stocks())
                .thenReturn(List.of(stock("GGAL", "6005", "0"), stock("YPFD", "42000", "0")))
                .thenReturn(List.of(stock("GGAL", "6100", "0")));
        market.quote(ggal);

        advance(PRICES_REFRESH.plusSeconds(1));
        market.quote(ggal);

        assertThat(market.quote(ggal).price()).isEqualByComparingTo("6100");
        assertThat(market.quote(ypfd).price()).isEqualByComparingTo("42000");
    }

    @Test
    void withoutAnyPreviousValue_aFailureIs503() {
        when(data912.stocks()).thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> market.quote(ggal))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage(LiveMarketData.UNAVAILABLE_MESSAGE);
    }

    @Test
    void aSymbolTheSourceDoesNotHave_is503() {
        when(data912.stocks()).thenReturn(List.of(stock("GGAL", "6005", "0")));

        assertThatThrownBy(() -> market.quote(ypfd))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("YPFD");
    }

    @Test
    void theCurrentRateIsTheSellPrice() {
        Instant published = Instant.parse("2026-09-29T14:57:00Z");
        when(dolarApi.current(FxRateType.BLUE))
                .thenReturn(new DolarApiClient.Response(new BigDecimal("1540"), new BigDecimal("1560"), published));

        FxQuote quote = market.currentQuote();

        assertThat(quote.buy()).isEqualByComparingTo("1540");
        assertThat(market.currentRate()).isEqualByComparingTo("1560");
        assertThat(quote.updatedAt()).isEqualTo(published);
    }

    @Test
    void historicalRate_usesTheLastDayWithAQuote() {
        when(argentinaDatos.history(FxRateType.BLUE)).thenReturn(List.of(
                new ArgentinaDatosClient.Entry(new BigDecimal("1480"), new BigDecimal("1500"), LocalDate.of(2026, 9, 25)),  // viernes
                new ArgentinaDatosClient.Entry(new BigDecimal("1530"), new BigDecimal("1550"), LocalDate.of(2026, 9, 28)))); // lunes

        assertThat(market.rateOn(LocalDate.of(2026, 9, 25))).isEqualByComparingTo("1500");
        assertThat(market.rateOn(LocalDate.of(2026, 9, 27))).isEqualByComparingTo("1500"); // domingo → viernes
        assertThat(market.rateOn(LocalDate.of(2026, 9, 28))).isEqualByComparingTo("1550");
        assertThat(market.rateOn(LocalDate.of(2026, 10, 2))).isEqualByComparingTo("1550"); // todavía sin publicar
        assertThat(market.rateOn(LocalDate.of(2010, 1, 1))).isEqualByComparingTo("1500");  // antes de la serie
        verify(argentinaDatos, times(1)).history(FxRateType.BLUE); // toda la serie en un solo pedido
    }

    private void advance(Duration duration) {
        nanos.addAndGet(duration.toNanos());
    }

    private static Data912Client.Quote stock(String symbol, String price, String change) {
        return new Data912Client.Quote(symbol, new BigDecimal(price), new BigDecimal(change));
    }
}
