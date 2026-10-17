package com.portfolix.api.performance;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.performance.PerformanceCalculator.AssetPeriod;
import com.portfolix.api.performance.PerformanceCalculator.Flow;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionType;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos hechos a mano, en un período de un año (2025, 365 días). La moneda es siempre la del activo.
 */
class PerformanceCalculatorTest {

    private static final LocalDate FROM = LocalDate.of(2025, 1, 1);
    private static final LocalDate TO = LocalDate.of(2025, 12, 31);
    private static final Function<LocalDate, BigDecimal> SAME_CURRENCY = date -> BigDecimal.ONE;
    private static final Portfolio PORTFOLIO =
            new Portfolio(new User("juan@email.com", "hash", "Juan", Instant.now()), "Jubilación");
    private static final Asset AAPL = new Asset("AAPL", "Apple Inc.", AssetType.CEDEAR, Currency.ARS);

    @Test
    void result_isEndMinusStartMinusBuysPlusSales() {
        // Precio 100 hasta junio y 125 desde julio.
        Function<LocalDate, BigDecimal> price = day -> day.isBefore(LocalDate.of(2025, 7, 1))
                ? new BigDecimal("100") : new BigDecimal("125");

        AssetPeriod period = PerformanceCalculator.asset(List.of(
                tx(TransactionType.BUY, "10", "100", LocalDate.of(2024, 12, 1)),  // antes: solo es la tenencia inicial
                tx(TransactionType.BUY, "4", "125", LocalDate.of(2025, 7, 1)),    // compra 500
                tx(TransactionType.SELL, "2", "100", LocalDate.of(2025, 10, 1)),  // venta 200
                tx(TransactionType.BUY, "1", "130", LocalDate.of(2026, 1, 5))     // después: se ignora
        ), FROM, TO, price, SAME_CURRENCY);

        assertThat(period.startQuantity()).isEqualByComparingTo("10");
        assertThat(period.startValue()).isEqualByComparingTo("1000");  // 10 × 100 al 31/12/2024
        assertThat(period.bought()).isEqualByComparingTo("500");
        assertThat(period.sold()).isEqualByComparingTo("200");
        assertThat(period.endQuantity()).isEqualByComparingTo("12");
        assertThat(period.endValue()).isEqualByComparingTo("1500");    // 12 × 125
        // 1.500 − 1.000 − 500 + 200 = 200
        assertThat(period.endValue().subtract(period.startValue()).subtract(period.bought()).add(period.sold()))
                .isEqualByComparingTo("200");
        assertThat(period.flows()).hasSize(2);
    }

    @Test
    void operationsAreConvertedWithTheRateOfTheirDay() {
        // Dólar a 1.000 en enero y a 1.200 en marzo: cada compra con el de su día.
        Function<LocalDate, BigDecimal> rate = day -> day.getMonthValue() == 1
                ? new BigDecimal("1000") : new BigDecimal("1200");

        AssetPeriod period = PerformanceCalculator.asset(List.of(
                tx(TransactionType.BUY, "1", "10", LocalDate.of(2025, 1, 10)),
                tx(TransactionType.BUY, "1", "10", LocalDate.of(2025, 3, 10))
        ), FROM, TO, day -> BigDecimal.TEN, rate);

        assertThat(period.bought()).isEqualByComparingTo("22000"); // 10 × 1.000 + 10 × 1.200
    }

    @Test
    void unitValue_isOnlyAskedForDaysWithHoldings() {
        LocalDate bought = LocalDate.of(2025, 1, 3);
        // Antes de la compra no hace falta precio (podría no existir: un activo que todavía no cotizaba).
        Function<LocalDate, BigDecimal> price = day -> {
            assertThat(day).isAfterOrEqualTo(bought);
            return BigDecimal.TEN;
        };

        AssetPeriod period = PerformanceCalculator.asset(
                List.of(tx(TransactionType.BUY, "1", "10", bought)),
                FROM, LocalDate.of(2025, 1, 5), price, SAME_CURRENCY);

        assertThat(period.startValue()).isEqualByComparingTo("0");
        assertThat(period.dailyValues()).containsOnlyKeys(bought, bought.plusDays(1), bought.plusDays(2));
    }

    @Test
    void percent_buyingOnTheFirstDayAndSellingOnTheLast_isTheSimpleReturn() {
        List<Flow> flows = List.of(
                new Flow(FROM, TransactionType.BUY, new BigDecimal("1000")),
                new Flow(TO, TransactionType.SELL, new BigDecimal("1100")));

        // Compra desde el principio de su día (peso 365/365), venta hasta el final del suyo (peso 0).
        assertThat(PerformanceCalculator.percent(FROM, TO, BigDecimal.ZERO, BigDecimal.ZERO, flows, new BigDecimal("100")))
                .isEqualByComparingTo("10");
    }

    @Test
    void percent_takesIntoAccountWhenTheMoneyCameIn() {
        // Tenías 1.000 todo el año y ganaste 500; el último día pusiste 10.000 que quedaron igual.
        List<Flow> flows = List.of(new Flow(TO, TransactionType.BUY, new BigDecimal("10000")));

        BigDecimal percent = PerformanceCalculator.percent(FROM, TO, new BigDecimal("1000"), new BigDecimal("11500"),
                flows, new BigDecimal("500"));

        // 500 / (1.000 + 10.000 × 1/365) = 500 / 1.027,40. Sobre lo puesto en juego sería 500 / 11.000 = 4,55 %.
        assertThat(percent).isEqualByComparingTo("48.67");
    }

    @Test
    void percent_aSaleStopsCountingAfterItsDay() {
        // 1.000 al empezar; el 2 de julio vendiste 500 (quedan 182 de 365 días) y lo que quedó terminó en 600.
        List<Flow> flows = List.of(new Flow(LocalDate.of(2025, 7, 2), TransactionType.SELL, new BigDecimal("500")));

        BigDecimal percent = PerformanceCalculator.percent(FROM, TO, new BigDecimal("1000"), new BigDecimal("600"),
                flows, new BigDecimal("100"));

        // Capital promedio = 1.000 − 500 × 182/365 = 750,68 → 100 / 750,68
        assertThat(ChronoUnit.DAYS.between(LocalDate.of(2025, 7, 2), TO)).isEqualTo(182);
        assertThat(percent).isEqualByComparingTo("13.32");
    }

    @Test
    void percent_withNothingAtTheStart_theRangeStartsWithTheFirstBuy() {
        // Compraste 1.000 en octubre y terminó valiendo 1.100. Contando el año entero, esa plata pesaría
        // 92/365 y daría 39,7 %; en realidad ganó 10 % en el tiempo que estuvo invertida.
        List<Flow> flows = List.of(new Flow(LocalDate.of(2025, 10, 1), TransactionType.BUY, new BigDecimal("1000")));

        assertThat(PerformanceCalculator.percent(FROM, TO, BigDecimal.ZERO, new BigDecimal("1100"), flows,
                new BigDecimal("100"))).isEqualByComparingTo("10");
    }

    @Test
    void percent_withNothingAtTheEnd_theRangeEndsWithTheLastSale() {
        // Tenías 1.000 y en junio vendiste todo por 1.100: ganó 10 % mientras estuvo invertido.
        List<Flow> flows = List.of(new Flow(LocalDate.of(2025, 6, 30), TransactionType.SELL, new BigDecimal("1100")));

        assertThat(PerformanceCalculator.percent(FROM, TO, new BigDecimal("1000"), BigDecimal.ZERO, flows,
                new BigDecimal("100"))).isEqualByComparingTo("10");
    }

    @Test
    void percent_hasTheSignOfTheResult() {
        List<Flow> flows = List.of(new Flow(LocalDate.of(2025, 3, 1), TransactionType.BUY, new BigDecimal("2000")));

        assertThat(PerformanceCalculator.percent(FROM, TO, new BigDecimal("1000"), new BigDecimal("2700"), flows,
                new BigDecimal("-300"))).isNegative();
    }

    @Test
    void percent_withoutCapital_isNull() {
        assertThat(PerformanceCalculator.percent(FROM, TO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), BigDecimal.ZERO))
                .isNull();
    }

    @Test
    void chartDays_oneADay_fromTheDayBeforeTheStart() {
        assertThat(PerformanceCalculator.chartDays(FROM, LocalDate.of(2025, 1, 3))).containsExactly(
                LocalDate.of(2024, 12, 31), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3));
        assertThat(PerformanceCalculator.chartDays(FROM, TO)).hasSize(366); // un año entero sigue siendo diario
    }

    @Test
    void chartDays_longRanges_goWeekByWeek_endingOnTheLastDay() {
        List<LocalDate> days = PerformanceCalculator.chartDays(LocalDate.of(2023, 1, 1), TO);

        assertThat(days.getFirst()).isEqualTo(LocalDate.of(2022, 12, 31));
        assertThat(days.getLast()).isEqualTo(TO);
        for (int i = 2; i < days.size(); i++) {
            assertThat(ChronoUnit.DAYS.between(days.get(i - 1), days.get(i))).isEqualTo(7);
        }
        assertThat(days.size()).isLessThan(160);
    }

    private static Transaction tx(TransactionType type, String quantity, String price, LocalDate date) {
        return new Transaction(PORTFOLIO, AAPL, type, new BigDecimal(quantity), new BigDecimal(price), date, null);
    }
}
