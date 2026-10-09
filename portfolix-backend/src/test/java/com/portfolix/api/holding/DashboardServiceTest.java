package com.portfolix.api.holding;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.holding.dto.DashboardSummaryResponse;
import com.portfolix.api.holding.dto.DashboardSummaryResponse.TypeAllocation;
import com.portfolix.api.market.FxQuote;
import com.portfolix.api.market.FxRateProvider;
import com.portfolix.api.market.FxRateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private static final Long USER_ID = 1L;

    private final HoldingService holdingService = mock(HoldingService.class);
    private final FxRateProvider fxRateProvider = mock(FxRateProvider.class);
    private final DashboardService service = new DashboardService(holdingService, fxRateProvider);

    private final Asset aapl = new Asset("AAPL", "Apple Inc.", AssetType.CEDEAR, Currency.ARS);
    private final Asset btc = new Asset("BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD);
    private final Asset eth = new Asset("ETH", "Ethereum", AssetType.CRYPTO, Currency.USD);

    @BeforeEach
    void setUp() {
        // Compra 1.380, venta 1.400: se convierte con la venta.
        when(fxRateProvider.currentQuote())
                .thenReturn(new FxQuote(FxRateType.BLUE, new BigDecimal("1380"), new BigDecimal("1400"), null));
    }

    @Test
    void pricesUpdatedAt_isTheOldestPriceAmongTheOpenPositions() {
        Instant tenAm = Instant.parse("2026-09-29T13:00:00Z");
        Instant elevenAm = Instant.parse("2026-09-29T14:00:00Z");
        positions(
                new AssetPosition(aapl, position("1", "10"), BigDecimal.ONE, elevenAm),
                new AssetPosition(eth, position("1", "10"), BigDecimal.ONE, tenAm),
                // Vendido por completo: su precio no se muestra, así que no cuenta.
                new AssetPosition(btc, position("0", "0"), BigDecimal.ONE, Instant.parse("2026-09-28T10:00:00Z")));

        assertThat(service.summary(USER_ID, null, Currency.ARS).pricesUpdatedAt()).isEqualTo(tenAm);
    }

    @Test
    void summary_addsUpThePositions_andTheTotalPnlIncludesSales() {
        positions(
                // 15 AAPL con costo 2.000 (ganó 500 vendiendo; compró por 3.000 en total), hoy a 300 → 4.500
                position(aapl, "15", "2000", "500", "3000", "300"),
                // BTC vendido por completo: ganó 50 (había comprado por 100)
                position(btc, "0", "0", "50", "100", "160"),
                // 2 ETH con costo 20, hoy a 20 → 40
                position(eth, "2", "20", "0", "20", "20"));

        DashboardSummaryResponse summary = service.summary(USER_ID, null, Currency.ARS);

        assertThat(summary.currentValue()).isEqualByComparingTo("4540");
        assertThat(summary.investedCapital()).isEqualByComparingTo("2020");
        assertThat(summary.unrealizedPnl()).isEqualByComparingTo("2520");   // 4.540 − 2.020
        assertThat(summary.realizedPnl()).isEqualByComparingTo("550");      // 500 + 50
        assertThat(summary.totalPnl()).isEqualByComparingTo("3070");        // 2.520 + 550
        assertThat(summary.totalPnlPercent()).isEqualByComparingTo("98.40"); // 3.070 / 3.120 comprados
        assertThat(summary.holdingsCount()).isEqualTo(2);                   // BTC ya no se tiene
        assertThat(summary.distribution())
                .extracting(TypeAllocation::type, a -> a.percent().toPlainString())
                .containsExactly(
                        tuple(AssetType.STOCK, "0.0"),
                        tuple(AssetType.CEDEAR, "99.1"),   // 99,119 %
                        tuple(AssetType.CRYPTO, "0.9"));   // 0,881 %: recibe la décima que faltaba para 100
        assertThat(summary.fx().type()).isEqualTo(FxRateType.BLUE);
        assertThat(summary.fx().rate()).isEqualByComparingTo("1400");
    }

    @Test
    void withoutPositions_everythingIsZero_andPercentagesAreNull() {
        positions();

        DashboardSummaryResponse summary = service.summary(USER_ID, null, Currency.ARS);

        assertThat(summary.currentValue()).isEqualByComparingTo("0");
        assertThat(summary.totalPnl()).isEqualByComparingTo("0");
        assertThat(summary.totalPnlPercent()).isNull();
        assertThat(summary.holdingsCount()).isZero();
        assertThat(summary.distribution())
                .extracting(TypeAllocation::type, TypeAllocation::percent)
                .containsExactly(tuple(AssetType.STOCK, null), tuple(AssetType.CEDEAR, null), tuple(AssetType.CRYPTO, null));
    }

    @Test
    void distribution_alwaysAddsUpTo100() {
        // Redondeando cada uno por separado daría 33,3 + 33,3 + 33,3 = 99,9.
        assertThat(percents(values("1", "1", "1"))).containsExactly("33.4", "33.3", "33.3");
        // 16,67 + 33,33 + 50: la décima que falta va al que más perdió al truncar (16,6 → 16,7).
        assertThat(percents(values("1", "2", "3"))).containsExactly("16.7", "33.3", "50.0");
    }

    private void positions(AssetPosition... positions) {
        when(holdingService.positions(USER_ID, null, null, Currency.ARS)).thenReturn(List.of(positions));
    }

    private static AssetPosition position(Asset asset, String quantity, String invested, String realized,
                                          String bought, String currentPrice) {
        Position position = new Position(new BigDecimal(quantity), new BigDecimal(invested),
                new BigDecimal(realized), new BigDecimal(bought));
        return new AssetPosition(asset, position, new BigDecimal(currentPrice), null);
    }

    private static Position position(String quantity, String invested) {
        return new Position(new BigDecimal(quantity), new BigDecimal(invested), BigDecimal.ZERO, new BigDecimal(invested));
    }

    private static Map<AssetType, BigDecimal> values(String stock, String cedear, String crypto) {
        Map<AssetType, BigDecimal> values = new EnumMap<>(AssetType.class);
        values.put(AssetType.STOCK, new BigDecimal(stock));
        values.put(AssetType.CEDEAR, new BigDecimal(cedear));
        values.put(AssetType.CRYPTO, new BigDecimal(crypto));
        return values;
    }

    private static List<String> percents(Map<AssetType, BigDecimal> values) {
        return DashboardService.distribution(values).stream()
                .map(allocation -> allocation.percent().toPlainString())
                .toList();
    }
}
