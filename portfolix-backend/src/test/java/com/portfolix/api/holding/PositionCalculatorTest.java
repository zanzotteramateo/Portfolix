package com.portfolix.api.holding;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionType;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionCalculatorTest {

    private static final Function<LocalDate, BigDecimal> SAME_CURRENCY = date -> BigDecimal.ONE;
    private static final Portfolio PORTFOLIO =
            new Portfolio(new User("juan@email.com", "hash", "Juan", Instant.now()), "Jubilación");
    private static final Asset AAPL = new Asset("AAPL", "Apple Inc.", AssetType.CEDEAR, Currency.ARS);
    private static final Asset BTC = new Asset("BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD);

    @Test
    void weightedAverage_salesDoNotChangeTheAveragePrice() {
        Position position = PositionCalculator.calculate(List.of(
                tx(AAPL, TransactionType.BUY, "10", "100", 1),
                tx(AAPL, TransactionType.BUY, "10", "200", 2),   // promedio (1.000 + 2.000) / 20 = 150
                tx(AAPL, TransactionType.SELL, "5", "300", 3)    // ganancia 5 × (300 − 150) = 750
        ), SAME_CURRENCY);

        assertThat(position.quantity()).isEqualByComparingTo("15");
        assertThat(position.investedCapital()).isEqualByComparingTo("2250"); // 15 × 150
        assertThat(position.realizedPnl()).isEqualByComparingTo("750");
        assertThat(position.totalBought()).isEqualByComparingTo("3000");
    }

    @Test
    void sellingEverything_leavesExactlyZeroInvested() {
        // 30,2 / 3 no es exacto: el costo de cada venta tiene decimales infinitos.
        Position position = PositionCalculator.calculate(List.of(
                tx(AAPL, TransactionType.BUY, "1", "10", 1),
                tx(AAPL, TransactionType.BUY, "2", "10.1", 2),
                tx(AAPL, TransactionType.SELL, "1", "12", 3),
                tx(AAPL, TransactionType.SELL, "2", "9", 4)
        ), SAME_CURRENCY);

        assertThat(position.quantity()).isEqualByComparingTo("0");
        assertThat(position.investedCapital()).isEqualByComparingTo("0");
        // Vendiste por 30 lo que te costó 30,2: sin restos de redondeo.
        assertThat(position.realizedPnl()).isEqualByComparingTo("-0.2");
    }

    @Test
    void eachOperationIsConvertedWithTheRateOfItsDay() {
        // BTC se opera en USD y se muestra en ARS; el dólar cambia día a día.
        Map<LocalDate, BigDecimal> arsPerUsd = Map.of(
                day(1), new BigDecimal("1000"),
                day(2), new BigDecimal("1500"),
                day(3), new BigDecimal("2000"));

        Position position = PositionCalculator.calculate(List.of(
                tx(BTC, TransactionType.BUY, "1", "100", 1),     // 100 USD × 1000 = 100.000 ARS
                tx(BTC, TransactionType.BUY, "1", "100", 2),     // 100 USD × 1500 = 150.000 ARS
                tx(BTC, TransactionType.SELL, "1", "120", 3)     // 120 USD × 2000 = 240.000 ARS
        ), arsPerUsd::get);

        assertThat(position.quantity()).isEqualByComparingTo("1");
        assertThat(position.investedCapital()).isEqualByComparingTo("125000"); // promedio 125.000 ARS
        assertThat(position.realizedPnl()).isEqualByComparingTo("115000");     // 240.000 − 125.000
        assertThat(position.totalBought()).isEqualByComparingTo("250000");
    }

    @Test
    void withoutTransactions_everythingIsZero() {
        assertThat(PositionCalculator.calculate(List.of(), SAME_CURRENCY)).isEqualTo(Position.EMPTY);
    }

    @Test
    void sellingMoreThanTheHolding_fails() {
        List<Transaction> impossible = List.of(
                tx(AAPL, TransactionType.BUY, "1", "10", 1),
                tx(AAPL, TransactionType.SELL, "2", "10", 2));

        assertThatThrownBy(() -> PositionCalculator.calculate(impossible, SAME_CURRENCY))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Transaction tx(Asset asset, TransactionType type, String quantity, String price, int day) {
        return new Transaction(PORTFOLIO, asset, type, new BigDecimal(quantity), new BigDecimal(price), day(day), null);
    }

    private static LocalDate day(int day) {
        return LocalDate.of(2026, 9, day);
    }
}
