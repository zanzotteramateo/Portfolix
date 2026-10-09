package com.portfolix.api.transaction;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SellableQuantityTest {

    private static final Asset BTC = new Asset("BTC", "Bitcoin", AssetType.CRYPTO, Currency.USD);
    private static final Portfolio PORTFOLIO =
            new Portfolio(new User("juan@email.com", "hash", "Juan", Instant.now()), "Jubilación");

    @Test
    void withoutTransactions_nothingIsAvailable() {
        assertThat(SellableQuantity.at(List.of(), day(10))).isEqualByComparingTo("0");
    }

    @Test
    void saleAfterAllTransactions_canSellCurrentHolding() {
        var history = List.of(buy(1, "10"), sell(5, "3"), buy(8, "0.5"));

        assertThat(SellableQuantity.at(history, day(20))).isEqualByComparingTo("7.5");
    }

    @Test
    void saleBeforeTheFirstBuy_hasNothingAvailable() {
        var history = List.of(buy(10, "5"));

        assertThat(SellableQuantity.at(history, day(5))).isEqualByComparingTo("0");
    }

    @Test
    void backdatedSale_isLimitedByLaterSales() {
        // Compra 10 el día 1, vende 8 el día 20. Una venta del día 5 solo puede ser de 2:
        // si fuera mayor, el día 20 la tenencia quedaría negativa.
        var history = List.of(buy(1, "10"), sell(20, "8"));

        assertThat(SellableQuantity.at(history, day(5))).isEqualByComparingTo("2");
        assertThat(SellableQuantity.current(history)).isEqualByComparingTo("2");
    }

    @Test
    void backdatedSale_isLimitedByHoldingAtThatDate() {
        // Compra 2 el día 1 y 10 el día 15. Hoy hay 12, pero el día 5 solo había 2.
        var history = List.of(buy(1, "2"), buy(15, "10"));

        assertThat(SellableQuantity.at(history, day(5))).isEqualByComparingTo("2");
        assertThat(SellableQuantity.current(history)).isEqualByComparingTo("12");
    }

    @Test
    void saleOnTheSameDay_goesAfterThatDaysTransactions() {
        var history = List.of(buy(10, "3"), sell(10, "1"));

        assertThat(SellableQuantity.at(history, day(10))).isEqualByComparingTo("2");
    }

    @Test
    void editedSale_keepsItsPlaceWithinTheDay() {
        // Compra 10 el día 1 y 5 el día 10. Una venta editada al día 10 que se cargó antes que la compra
        // de ese día queda antes que ella (posición 1): solo dispone de 10, no de 15.
        var others = List.of(buy(1, "10"), buy(10, "5"));

        assertThat(SellableQuantity.at(others, 1)).isEqualByComparingTo("10");
        assertThat(SellableQuantity.at(others, 2)).isEqualByComparingTo("15");
    }

    @Test
    void editedSale_isLimitedByLaterSales() {
        var others = List.of(buy(1, "10"), sell(20, "8"));

        assertThat(SellableQuantity.at(others, 0)).isEqualByComparingTo("0");
        assertThat(SellableQuantity.at(others, 1)).isEqualByComparingTo("2");
    }

    @Test
    void validHistory_hasNoUncoveredSale() {
        var history = List.of(buy(1, "10"), sell(5, "10"), buy(8, "0.5"), sell(9, "0.5"));

        assertThat(SellableQuantity.firstUncoveredSale(history)).isEmpty();
    }

    @Test
    void withoutABuy_theSaleItCoveredIsUncovered() {
        // Era: compra 5 el día 1, compra 5 el día 10, venta 8 el día 20. Sin la compra del día 10:
        Transaction sale = sell(20, "8");

        assertThat(SellableQuantity.firstUncoveredSale(List.of(buy(1, "5"), sale))).containsSame(sale);
    }

    @Test
    void buyMovedAfterTheSale_leavesItUncovered() {
        Transaction sale = sell(5, "8");

        assertThat(SellableQuantity.firstUncoveredSale(List.of(sale, buy(10, "10")))).containsSame(sale);
    }

    @Test
    void reportsTheFirstUncoveredSale() {
        Transaction covered = sell(3, "1");
        Transaction firstUncovered = sell(5, "3");
        var history = List.of(buy(1, "2"), covered, firstUncovered, sell(7, "1"));

        assertThat(SellableQuantity.firstUncoveredSale(history)).containsSame(firstUncovered);
    }

    @Test
    void keepsCryptoPrecision() {
        var history = List.of(buy(1, "0.34210000"), sell(2, "0.00000001"));

        assertThat(SellableQuantity.at(history, day(3))).isEqualByComparingTo("0.34209999");
    }

    private static Transaction buy(int day, String quantity) {
        return tx(TransactionType.BUY, day, quantity);
    }

    private static Transaction sell(int day, String quantity) {
        return tx(TransactionType.SELL, day, quantity);
    }

    private static Transaction tx(TransactionType type, int day, String quantity) {
        return new Transaction(PORTFOLIO, BTC, type, new BigDecimal(quantity), BigDecimal.ONE, day(day), null);
    }

    private static LocalDate day(int day) {
        return LocalDate.of(2026, 9, day);
    }
}
