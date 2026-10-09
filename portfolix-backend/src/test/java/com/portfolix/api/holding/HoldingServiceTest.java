package com.portfolix.api.holding;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.holding.dto.HoldingResponse;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.market.FxRateProvider;
import com.portfolix.api.market.HistoryRange;
import com.portfolix.api.market.PriceHistory;
import com.portfolix.api.market.PriceHistoryProvider;
import com.portfolix.api.market.PricePoint;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Cálculo de posiciones con precios y dólar controlados (mocks), para verificar los números exactos.
 */
class HoldingServiceTest {

    private static final Long USER_ID = 1L;
    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 9, 2);
    private static final LocalDate DAY_3 = LocalDate.of(2026, 9, 3);

    private final TransactionService transactionService = mock(TransactionService.class);
    private final PortfolioService portfolioService = mock(PortfolioService.class);
    private final AssetService assetService = mock(AssetService.class);
    private final PriceProvider priceProvider = mock(PriceProvider.class);
    private final FxRateProvider fxRateProvider = mock(FxRateProvider.class);
    private final PriceHistoryProvider priceHistoryProvider = mock(PriceHistoryProvider.class);
    private final HoldingService service = new HoldingService(transactionService, portfolioService, assetService,
            priceProvider, priceHistoryProvider, new CurrencyConverter(fxRateProvider));

    private final User user = new User("juan@email.com", "hash", "Juan", Instant.now());
    private final Portfolio jubilacion = portfolio(10L, "Jubilación");
    private final Portfolio trading = portfolio(20L, "Trading");
    private final Asset aapl = asset(100L, "AAPL", AssetType.CEDEAR, Currency.ARS);
    private final Asset btc = asset(200L, "BTC", AssetType.CRYPTO, Currency.USD);
    private final Asset eth = asset(300L, "ETH", AssetType.CRYPTO, Currency.USD);

    @BeforeEach
    void setUp() {
        when(fxRateProvider.currentRate()).thenReturn(new BigDecimal("1000"));
        when(fxRateProvider.rateOn(any())).thenReturn(new BigDecimal("1000"));
    }

    @Test
    void allPortfolios_isTheSumOfEachPortfolioCalculatedSeparately() {
        history(tx(jubilacion, aapl, TransactionType.BUY, "10", "100", DAY_1),
                tx(trading, aapl, TransactionType.BUY, "10", "200", DAY_2),
                tx(trading, aapl, TransactionType.SELL, "5", "300", DAY_3));
        when(priceProvider.quote(aapl)).thenReturn(price("300"));

        HoldingResponse holding = service.list(USER_ID, null, Currency.ARS).holdings().getFirst();

        // Jubilación: 10 con costo 1.000. Trading: quedan 5 con costo 1.000 (promedio 200) y ganó 500.
        // Calcular todo junto daría otro promedio (150), invertido 2.250 y ganancia 750: no cerraría
        // con la suma de lo que muestra cada portafolio.
        assertThat(holding.quantity()).isEqualByComparingTo("15");
        assertThat(holding.investedCapital()).isEqualByComparingTo("2000");
        assertThat(holding.averagePrice()).isEqualByComparingTo("133.33333333");
        assertThat(holding.realizedPnl()).isEqualByComparingTo("500");
        assertThat(holding.currentValue()).isEqualByComparingTo("4500");
        assertThat(holding.pnl()).isEqualByComparingTo("2500");
        assertThat(holding.pnlPercent()).isEqualByComparingTo("125");
    }

    @Test
    void sevenDayTrend_comesFromTheCachedHistory_orIsNullWhileItLoads() {
        history(tx(jubilacion, aapl, TransactionType.BUY, "1", "100", DAY_1),
                tx(trading, btc, TransactionType.BUY, "1", "100", DAY_1));
        when(priceProvider.quote(aapl)).thenReturn(price("120"));
        when(priceProvider.quote(btc)).thenReturn(price("110"));
        PriceHistory aaplWeek = PriceHistory.of("AAPL", Currency.ARS, HistoryRange.WEEK, List.of(
                new PricePoint(Instant.parse("2026-09-25T20:00:00Z"), new BigDecimal("100")),
                new PricePoint(Instant.parse("2026-10-01T20:00:00Z"), new BigDecimal("120"))));
        when(priceHistoryProvider.cachedHistory(aapl, HistoryRange.WEEK)).thenReturn(Optional.of(aaplWeek));
        // BTC: el historial todavía no se cargó (el mock devuelve Optional vacío).

        List<HoldingResponse> holdings = service.list(USER_ID, null, Currency.ARS).holdings();

        HoldingResponse aaplRow = holdings.stream().filter(h -> h.symbol().equals("AAPL")).findFirst().orElseThrow();
        assertThat(aaplRow.change7dPercent()).isEqualByComparingTo("20");
        assertThat(aaplRow.sparkline7d()).extracting(BigDecimal::toPlainString).containsExactly("100", "120");
        HoldingResponse btcRow = holdings.stream().filter(h -> h.symbol().equals("BTC")).findFirst().orElseThrow();
        assertThat(btcRow.change7dPercent()).isNull();
        assertThat(btcRow.sparkline7d()).isNull();
    }

    @Test
    void anotherCurrency_convertsWhatYouPaidWithTheRateOfEachDay() {
        // Compraste un CEDEAR por $100.000 con el dólar a $500 (US$200). Hoy vale $250.000 con el dólar a $1.250 (US$200).
        history(tx(jubilacion, aapl, TransactionType.BUY, "1", "100000", DAY_1));
        when(fxRateProvider.rateOn(DAY_1)).thenReturn(new BigDecimal("500"));
        when(fxRateProvider.currentRate()).thenReturn(new BigDecimal("1250"));
        when(priceProvider.quote(aapl)).thenReturn(price("250000"));

        HoldingResponse inUsd = service.list(USER_ID, null, Currency.USD).holdings().getFirst();
        assertThat(inUsd.investedCapital()).isEqualByComparingTo("200");
        assertThat(inUsd.currentPrice()).isEqualByComparingTo("200");
        assertThat(inUsd.currentValue()).isEqualByComparingTo("200");
        assertThat(inUsd.pnlPercent()).isEqualByComparingTo("0");       // en dólares no ganaste nada

        HoldingResponse inArs = service.list(USER_ID, null, Currency.ARS).holdings().getFirst();
        assertThat(inArs.investedCapital()).isEqualByComparingTo("100000");
        assertThat(inArs.currentValue()).isEqualByComparingTo("250000");
        assertThat(inArs.pnlPercent()).isEqualByComparingTo("150");     // en pesos, +150 %
    }

    @Test
    void list_hidesSoldOutAssets_andSortsByCurrentValueDescending() {
        history(tx(trading, btc, TransactionType.BUY, "1", "100", DAY_1),
                tx(trading, btc, TransactionType.SELL, "1", "150", DAY_2),
                tx(jubilacion, aapl, TransactionType.BUY, "10", "1000", DAY_1),
                tx(trading, eth, TransactionType.BUY, "2", "10", DAY_1));
        when(priceProvider.quote(btc)).thenReturn(price("160"));
        when(priceProvider.quote(aapl)).thenReturn(price("1100"));  // 10 × 1.100 = 11.000 ARS
        when(priceProvider.quote(eth)).thenReturn(price("20"));     // 2 × 20 USD × 1.000 = 40.000 ARS

        List<HoldingResponse> holdings = service.list(USER_ID, null, Currency.ARS).holdings();

        assertThat(holdings).extracting(HoldingResponse::symbol).containsExactly("ETH", "AAPL");
    }

    @Test
    void detail_ofASoldOutAsset_showsItsRealizedPnl() {
        List<Transaction> btcHistory = List.of(
                tx(trading, btc, TransactionType.BUY, "1", "100", DAY_1),
                tx(trading, btc, TransactionType.SELL, "1", "150", DAY_2));
        when(assetService.getBySymbol("btc")).thenReturn(btc);
        when(transactionService.findForPositions(USER_ID, null, 200L)).thenReturn(btcHistory);
        when(priceProvider.quote(btc)).thenReturn(price("160"));

        HoldingResponse detail = service.get(USER_ID, "btc", null, Currency.USD);

        assertThat(detail.quantity()).isEqualByComparingTo("0");
        assertThat(detail.averagePrice()).isNull();
        assertThat(detail.investedCapital()).isEqualByComparingTo("0");
        assertThat(detail.pnlPercent()).isNull();
        assertThat(detail.realizedPnl()).isEqualByComparingTo("50");
        assertThat(detail.currentPrice()).isEqualByComparingTo("160");
    }

    @Test
    void detail_ofAnAssetNeverOperated_isNotFound() {
        when(assetService.getBySymbol("AAPL")).thenReturn(aapl);
        when(transactionService.findForPositions(USER_ID, null, 100L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.get(USER_ID, "AAPL", null, Currency.ARS))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(HoldingService.NO_OPERATIONS_MESSAGE);
    }

    private static PriceQuote price(String price) {
        return new PriceQuote(new BigDecimal(price), null, null);
    }

    private void history(Transaction... transactions) {
        when(transactionService.findForPositions(USER_ID, null, null)).thenReturn(List.of(transactions));
    }

    private Transaction tx(Portfolio portfolio, Asset asset, TransactionType type, String quantity, String price,
                           LocalDate date) {
        return new Transaction(portfolio, asset, type, new BigDecimal(quantity), new BigDecimal(price), date, null);
    }

    private Portfolio portfolio(Long id, String name) {
        Portfolio portfolio = new Portfolio(user, name);
        ReflectionTestUtils.setField(portfolio, "id", id);
        return portfolio;
    }

    private static Asset asset(Long id, String symbol, AssetType type, Currency currency) {
        Asset asset = new Asset(symbol, symbol, type, currency);
        ReflectionTestUtils.setField(asset, "id", id);
        return asset;
    }
}
