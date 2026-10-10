package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.BusinessCalendar;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Caché del historial con las fuentes simuladas, el reloj de Caffeine falso y el refresco en el mismo hilo.
 */
class LiveMarketHistoryTest {

    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z"); // viernes, 12 h en Buenos Aires

    private final CoinGeckoClient coinGecko = mock(CoinGeckoClient.class);
    private final Data912Client data912 = mock(Data912Client.class);
    private final PriceProvider priceProvider = mock(PriceProvider.class);

    private final Asset ggal = new Asset("GGAL", "Grupo Financiero Galicia", AssetType.STOCK, Currency.ARS);
    private final Asset btc = new Asset("BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD);
    private final Asset usdt = new Asset("USDT", "Tether", AssetType.CRYPTO, Currency.USD);

    private LiveMarketHistory history;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        MarketProperties properties = new MarketProperties(
                MarketProperties.Provider.LIVE,
                FxRateType.BLUE,
                new MarketProperties.Refresh(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(12),
                        Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofHours(6)),
                new MarketProperties.Sources("https://a.test", "https://b.test", "https://c.test", "https://d.test"),
                new MarketProperties.Fixed(BigDecimal.ONE, Map.of()));
        BusinessCalendar calendar = new BusinessCalendar(clock, ZoneId.of("America/Argentina/Buenos_Aires"));
        AtomicLong nanos = new AtomicLong();
        history = new LiveMarketHistory(coinGecko, data912, priceProvider, calendar, properties, clock,
                nanos::get, Runnable::run);
        when(priceProvider.quote(ggal)).thenReturn(new PriceQuote(new BigDecimal("6100"), null, NOW));
    }

    @Test
    void cachedHistory_neverWaits_theFirstTimeItLoadsInTheBackground() {
        when(data912.history(AssetType.STOCK, "GGAL")).thenReturn(List.of(
                candle(9, 25, "6000"), candle(10, 1, "6050")));

        // Primera vez: no está cargado → vacío (la tabla sale sin minigráfico) y se pide en segundo plano.
        assertThat(history.cachedHistory(ggal, HistoryRange.WEEK)).isEmpty();
        // En producción esa carga corre en otro hilo; acá en el mismo, así que ya está.
        PriceHistory week = history.cachedHistory(ggal, HistoryRange.WEEK).orElseThrow();

        assertThat(week.points()).extracting(PricePoint::price).extracting(BigDecimal::toPlainString)
                .containsExactly("6000", "6050", "6100");
        verify(data912, times(1)).history(AssetType.STOCK, "GGAL");
    }

    @Test
    void cachedHistory_neverFails_evenIfTheSourceIsDown() {
        when(data912.history(AssetType.STOCK, "GGAL")).thenThrow(new ResourceAccessException("timeout"));

        assertThat(history.cachedHistory(ggal, HistoryRange.WEEK)).isEmpty();
        assertThat(history.cachedHistory(ggal, HistoryRange.WEEK)).isEmpty();
    }

    @Test
    void history_waitsForTheSource_andIs503IfItNeverResponded() {
        when(data912.history(AssetType.STOCK, "GGAL")).thenThrow(new ResourceAccessException("timeout"));

        assertThatThrownBy(() -> history.history(ggal, HistoryRange.DAY))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void bymaDayAndWeek_shareTheSameDailyCloses() {
        when(data912.history(AssetType.STOCK, "GGAL")).thenReturn(List.of(
                candle(9, 25, "6000"), candle(10, 1, "6050")));

        assertThat(history.history(ggal, HistoryRange.DAY).changePercent()).isEqualByComparingTo("0.83"); // 6100 / 6050
        assertThat(history.history(ggal, HistoryRange.WEEK).changePercent()).isEqualByComparingTo("1.67"); // 6100 / 6000
        verify(data912, times(1)).history(AssetType.STOCK, "GGAL"); // un solo pedido de ~600 KB para los dos
    }

    @Test
    void crypto_readsPointsFromCoinGecko() {
        when(coinGecko.marketChart("bitcoin", 7)).thenReturn(List.of(
                point(0, "80000"), point(1, "81000"), point(2, "82000"), point(3, "84000")));

        PriceHistory week = history.history(btc, HistoryRange.WEEK);

        assertThat(week.points()).extracting(PricePoint::price).extracting(BigDecimal::toPlainString)
                .containsExactly("80000", "81000", "82000", "84000");
        assertThat(week.changePercent()).isEqualByComparingTo("5"); // 84000 / 80000
        assertThat(week.currency()).isEqualTo(Currency.USD);
    }

    @Test
    void usdt_isFlatAndNeverAsksCoinGecko() {
        assertThat(history.history(usdt, HistoryRange.DAY).points().getFirst().price()).isEqualByComparingTo("1");
        verify(coinGecko, never()).marketChart(anyString(), anyInt());
    }

    private static Data912Client.Candle candle(int month, int day, String close) {
        return new Data912Client.Candle(LocalDate.of(2026, month, day), new BigDecimal(close));
    }

    /** Un punto de {@code market_chart}: {@code [epochMillis, precio]}. */
    private static List<BigDecimal> point(int hour, String price) {
        Instant time = Instant.parse("2026-09-25T00:00:00Z").plus(Duration.ofHours(hour));
        return List.of(BigDecimal.valueOf(time.toEpochMilli()), new BigDecimal(price));
    }
}
