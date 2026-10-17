package com.portfolix.api.performance;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.BusinessCalendar;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.performance.dto.PerformanceResponse;
import com.portfolix.api.performance.dto.PerformanceResponse.AssetPerformance;
import com.portfolix.api.market.ClosesRequest;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.market.DailyCloses;
import com.portfolix.api.market.FxRateProvider;
import com.portfolix.api.market.PriceHistoryProvider;
import com.portfolix.api.market.PriceProvider;
import com.portfolix.api.market.PriceQuote;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.portfolio.PortfolioService;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionService;
import com.portfolix.api.transaction.TransactionType;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Análisis con precios, cierres y dólar controlados (mocks), para verificar los números exactos.
 * El dólar vale 1.000 hasta junio de 2025 y 1.500 desde julio; hoy, 1.600.
 */
class PerformanceServiceTest {

    private static final Long USER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z"); // 12 h en Buenos Aires
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 12, 31);

    private final TransactionService transactionService = mock(TransactionService.class);
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final PriceProvider priceProvider = mock(PriceProvider.class);
    private final PriceHistoryProvider priceHistoryProvider = mock(PriceHistoryProvider.class);
    private final FxRateProvider fxRateProvider = mock(FxRateProvider.class);
    private final PerformanceService service = new PerformanceService(transactionService, portfolioService,
            priceProvider, priceHistoryProvider, new CurrencyConverter(fxRateProvider),
            new BusinessCalendar(Clock.fixed(NOW, ZoneOffset.UTC), ZoneId.of("America/Argentina/Buenos_Aires")));

    private final Portfolio portfolio = new Portfolio(new User("juan@email.com", "hash", "Juan", NOW), "Jubilación");
    private final Asset ggal = asset(1L, "GGAL", AssetType.STOCK, Currency.ARS);
    private final Asset btc = asset(2L, "BTC", AssetType.CRYPTO, Currency.USD);
    private final Asset eth = asset(3L, "ETH", AssetType.CRYPTO, Currency.USD);
    private final Asset bnb = asset(4L, "BNB", AssetType.CRYPTO, Currency.USD);
    private final Asset ada = asset(5L, "ADA", AssetType.CRYPTO, Currency.USD);

    @BeforeEach
    void setUp() {
        when(fxRateProvider.rateOn(any())).thenAnswer(invocation ->
                invocation.<LocalDate>getArgument(0).isBefore(LocalDate.of(2025, 7, 1))
                        ? new BigDecimal("1000") : new BigDecimal("1500"));
        when(fxRateProvider.currentRate()).thenReturn(new BigDecimal("1600"));
    }

    /**
     * 2025 en dólares:
     * <ul>
     *   <li>GGAL: 100 desde antes; valían 5.000 ARS (US$500) y terminaron en 9.000 ARS (US$600) → +100</li>
     *   <li>BTC: compra 0,1 a 50.000 en marzo; cierra el año en 60.000 → +1.000</li>
     *   <li>ETH: 1 desde antes, valía 3.500; se vendió en mayo a 2.500 → −1.000</li>
     *   <li>BNB: compra 1 a 600 en septiembre, sin precio de mercado → se usa el de la compra → 0</li>
     *   <li>ADA: se compró y vendió en 2024 → no aparece</li>
     * </ul>
     */
    private void givenTheYear2025() {
        when(transactionService.findForPositions(USER_ID, null, null)).thenReturn(List.of(
                tx(eth, TransactionType.BUY, "1", "3000", LocalDate.of(2024, 1, 10)),
                tx(ada, TransactionType.BUY, "10", "1", LocalDate.of(2024, 2, 1)),
                tx(ada, TransactionType.SELL, "10", "2", LocalDate.of(2024, 3, 1)),
                tx(ggal, TransactionType.BUY, "100", "4000", LocalDate.of(2024, 6, 1)),
                tx(btc, TransactionType.BUY, "0.1", "50000", LocalDate.of(2025, 3, 1)),
                tx(eth, TransactionType.SELL, "1", "2500", LocalDate.of(2025, 5, 1)),
                tx(bnb, TransactionType.BUY, "1", "600", LocalDate.of(2025, 9, 1)),
                tx(btc, TransactionType.BUY, "0.1", "70000", LocalDate.of(2026, 2, 1)))); // después del período
        when(priceHistoryProvider.dailyCloses(any())).thenReturn(Map.of(
                "GGAL", closes(Map.of(LocalDate.of(2024, 12, 31), "5000", TO, "9000")),
                "BTC", closes(Map.of(LocalDate.of(2025, 3, 1), "50000", TO, "60000")),
                "ETH", closes(Map.of(LocalDate.of(2024, 12, 31), "3500")),
                "BNB", closes(Map.of())));
    }

    @Test
    void aPastPeriod_isValuedWithTheClosesAndTheRateOfEachDay() {
        givenTheYear2025();

        PerformanceResponse response = service.performance(USER_ID, null, null, FROM, TO, Currency.USD);

        assertThat(response.assets()).extracting(AssetPerformance::symbol).containsExactly("BTC", "GGAL", "BNB", "ETH");
        AssetPerformance ggalRow = row(response, "GGAL");
        assertThat(ggalRow.startValue()).isEqualByComparingTo("500");  // 100 × 5.000 / 1.000
        assertThat(ggalRow.endValue()).isEqualByComparingTo("600");    // 100 × 9.000 / 1.500
        assertThat(ggalRow.pnl()).isEqualByComparingTo("100");
        assertThat(ggalRow.pnlPercent()).isEqualByComparingTo("20");
        AssetPerformance ethRow = row(response, "ETH");
        assertThat(ethRow.sold()).isEqualByComparingTo("2500");
        assertThat(ethRow.endQuantity()).isEqualByComparingTo("0");
        assertThat(ethRow.pnl()).isEqualByComparingTo("-1000");
        assertThat(ethRow.pnlPercent()).isEqualByComparingTo("-28.57"); // −1.000 / 3.500
        assertThat(row(response, "BTC").pnlPercent()).isEqualByComparingTo("20"); // arranca con la compra de marzo

        assertThat(response.startValue()).isEqualByComparingTo("4000");
        assertThat(response.endValue()).isEqualByComparingTo("7200");
        assertThat(response.bought()).isEqualByComparingTo("5600");
        assertThat(response.sold()).isEqualByComparingTo("2500");
        assertThat(response.pnl()).isEqualByComparingTo("100");        // 7.200 − 4.000 − 5.600 + 2.500
        assertThat(response.best().symbol()).isEqualTo("BTC");
        assertThat(response.worst().symbol()).isEqualTo("ETH");
        assertThat(response.pricesUpdatedAt()).isNull();               // no llega a hoy
        verify(priceProvider, never()).quote(any());
    }

    @Test
    void withoutAMarketPrice_theLastTradePriceIsUsed_andTheRowSaysSo() {
        givenTheYear2025();

        PerformanceResponse response = service.performance(USER_ID, null, null, FROM, TO, Currency.USD);

        AssetPerformance bnbRow = row(response, "BNB");
        assertThat(bnbRow.endValue()).isEqualByComparingTo("600");
        assertThat(bnbRow.estimatedPrice()).isTrue();
        assertThat(row(response, "BTC").estimatedPrice()).isFalse();
    }

    @Test
    void series_startsWithTheStartValue_andEndsWithTheEndValue() {
        givenTheYear2025();

        PerformanceResponse response = service.performance(USER_ID, null, null, FROM, TO, Currency.USD);

        assertThat(response.series()).hasSize(366);
        PerformanceResponse.ValuePoint first = response.series().getFirst();
        assertThat(first.date()).isEqualTo(LocalDate.of(2024, 12, 31));
        assertThat(first.value()).isEqualByComparingTo("4000");
        assertThat(first.netInvested()).isEqualByComparingTo("4000");
        PerformanceResponse.ValuePoint last = response.series().getLast();
        assertThat(last.value()).isEqualByComparingTo("7200");
        assertThat(last.netInvested()).isEqualByComparingTo("7100");    // 4.000 + 5.600 − 2.500
    }

    @SuppressWarnings("unchecked")
    @Test
    void closes_areAskedOnlyFromWhenEachAssetWasHeld() {
        givenTheYear2025();
        ArgumentCaptor<Collection<ClosesRequest>> requests = ArgumentCaptor.forClass(Collection.class);

        service.performance(USER_ID, null, null, FROM, TO, Currency.USD);

        verify(priceHistoryProvider).dailyCloses(requests.capture());
        assertThat(requests.getValue()).extracting(request -> request.asset().getSymbol(), ClosesRequest::from,
                        ClosesRequest::to)
                .containsExactlyInAnyOrder(
                        tuple("ETH", LocalDate.of(2024, 12, 31), TO),
                        tuple("GGAL", LocalDate.of(2024, 12, 31), TO),
                        tuple("BTC", LocalDate.of(2025, 3, 1), TO),
                        tuple("BNB", LocalDate.of(2025, 9, 1), TO));
    }

    @Test
    void typeFilter_leavesOnlyThatType() {
        givenTheYear2025();

        PerformanceResponse response = service.performance(USER_ID, null, AssetType.CRYPTO, FROM, TO, Currency.USD);

        assertThat(response.assets()).extracting(AssetPerformance::symbol).containsExactly("BTC", "BNB", "ETH");
        assertThat(response.pnl()).isEqualByComparingTo("0"); // +1.000 − 1.000
    }

    @Test
    void sinceTheBeginningUntilToday_usesTheCurrentPriceAndRate() {
        Instant priceTime = Instant.parse("2026-10-16T14:55:00Z");
        when(transactionService.findForPositions(USER_ID, null, null)).thenReturn(List.of(
                tx(ggal, TransactionType.BUY, "10", "1000", LocalDate.of(2026, 10, 1))));
        when(priceHistoryProvider.dailyCloses(any())).thenReturn(Map.of(
                "GGAL", closes(Map.of(LocalDate.of(2026, 10, 1), "1100"))));
        when(priceProvider.quote(ggal)).thenReturn(new PriceQuote(new BigDecimal("1500"), null, priceTime));

        PerformanceResponse response = service.performance(USER_ID, null, null, null, null, Currency.ARS);

        assertThat(response.from()).isEqualTo(LocalDate.of(2026, 10, 1)); // la primera operación
        assertThat(response.to()).isEqualTo(TODAY);
        assertThat(response.startValue()).isEqualByComparingTo("0");
        assertThat(response.endValue()).isEqualByComparingTo("15000");    // precio actual
        assertThat(response.pnl()).isEqualByComparingTo("5000");
        assertThat(response.pnlPercent()).isEqualByComparingTo("50");
        assertThat(response.worst()).isNull();                            // ninguno perdió
        assertThat(response.pricesUpdatedAt()).isEqualTo(priceTime);
        assertThat(response.series().getLast().value()).isEqualByComparingTo("15000");
    }

    @Test
    void today_isConvertedWithTheCurrentRate() {
        when(transactionService.findForPositions(USER_ID, null, null)).thenReturn(List.of(
                tx(btc, TransactionType.BUY, "1", "100", LocalDate.of(2026, 10, 1))));
        when(priceHistoryProvider.dailyCloses(any())).thenReturn(Map.of("BTC", closes(Map.of())));
        when(priceProvider.quote(btc)).thenReturn(new PriceQuote(new BigDecimal("100"), null, NOW));

        PerformanceResponse response = service.performance(USER_ID, null, null, null, null, Currency.ARS);

        assertThat(response.bought()).isEqualByComparingTo("150000");   // 100 × 1.500, el dólar de su día
        assertThat(response.endValue()).isEqualByComparingTo("160000"); // 100 × 1.600, el de hoy
    }

    @Test
    void withoutTransactions_everythingIsZero() {
        when(transactionService.findForPositions(USER_ID, null, null)).thenReturn(List.of());

        PerformanceResponse response = service.performance(USER_ID, null, null, FROM, TO, Currency.ARS);

        assertThat(response.assets()).isEmpty();
        assertThat(response.pnl()).isEqualByComparingTo("0");
        assertThat(response.pnlPercent()).isNull();
        assertThat(response.best()).isNull();
        verify(priceHistoryProvider, never()).dailyCloses(any());
    }

    @Test
    void aFutureEnd_isRejected() {
        assertThatThrownBy(() -> service.performance(USER_ID, null, null, FROM, TODAY.plusDays(1), Currency.ARS))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceService.FUTURE_END_MESSAGE)
                .extracting("field").isEqualTo("to");
    }

    @Test
    void aStartAfterTheEnd_isRejected() {
        assertThatThrownBy(() -> service.performance(USER_ID, null, null, TO, FROM, Currency.ARS))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PerformanceService.INVERTED_RANGE_MESSAGE)
                .extracting("field").isEqualTo("from");
    }

    @Test
    void someoneElsesPortfolio_is404() {
        when(portfolioService.getOwned(USER_ID, 99L)).thenThrow(new ResourceNotFoundException("Portafolio no encontrado"));

        assertThatThrownBy(() -> service.performance(USER_ID, 99L, null, FROM, TO, Currency.ARS))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static AssetPerformance row(PerformanceResponse response, String symbol) {
        return response.assets().stream().filter(row -> row.symbol().equals(symbol)).findFirst().orElseThrow();
    }

    private static DailyCloses closes(Map<LocalDate, String> closes) {
        TreeMap<LocalDate, BigDecimal> map = new TreeMap<>();
        closes.forEach((date, price) -> map.put(date, new BigDecimal(price)));
        return DailyCloses.of(map);
    }

    private Transaction tx(Asset asset, TransactionType type, String quantity, String price, LocalDate date) {
        return new Transaction(portfolio, asset, type, new BigDecimal(quantity), new BigDecimal(price), date, null);
    }

    private static Asset asset(Long id, String symbol, AssetType type, Currency currency) {
        Asset asset = new Asset(symbol, symbol, type, currency);
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
