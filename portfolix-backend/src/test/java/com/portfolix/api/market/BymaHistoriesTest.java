package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class BymaHistoriesTest {

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant NOW = Instant.parse("2026-10-02T15:00:00Z");
    private static final Asset GGAL = new Asset("GGAL", "Grupo Financiero Galicia", AssetType.STOCK, Currency.ARS);
    private static final PriceQuote LIVE = new PriceQuote(new BigDecimal("110"), null, NOW);

    /** Cierres de dos semanas (sin sábados ni domingos): del martes 22/9 al jueves 1/10. */
    private static NavigableMap<LocalDate, BigDecimal> closes() {
        NavigableMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        closes.put(day(9, 22), new BigDecimal("100"));
        closes.put(day(9, 23), new BigDecimal("101"));
        closes.put(day(9, 24), new BigDecimal("102"));
        closes.put(day(9, 25), new BigDecimal("103")); // viernes
        closes.put(day(9, 28), new BigDecimal("104")); // lunes
        closes.put(day(9, 29), new BigDecimal("105"));
        closes.put(day(9, 30), new BigDecimal("106"));
        closes.put(day(10, 1), new BigDecimal("107"));
        return closes;
    }

    @Test
    void week_startsAtTheCloseOfSevenDaysAgo_andEndsAtTheCurrentPrice() {
        // Hoy viernes 2/10: el rango arranca el viernes 25/9.
        PriceHistory history = build(HistoryRange.WEEK, closes(), day(10, 2));

        assertThat(history.points()).extracting(PricePoint::price).extracting(BigDecimal::toPlainString)
                .containsExactly("103", "104", "105", "106", "107", "110");
        assertThat(history.changePercent()).isEqualByComparingTo("6.80"); // 110 / 103 − 1
        // Cada cierre se ubica a las 17 h de Buenos Aires (20 h UTC); el último punto es la hora del precio actual.
        assertThat(history.points().getFirst().time()).isEqualTo(Instant.parse("2026-09-25T20:00:00Z"));
        assertThat(history.points().getLast().time()).isEqualTo(NOW);
    }

    @Test
    void week_whenItStartsOnAWeekend_theBaseIsTheFridayBefore() {
        // Hoy domingo 4/10: hace 7 días fue domingo 27/9, sin cotización → base viernes 25/9.
        PriceHistory history = build(HistoryRange.WEEK, closes(), day(10, 4));

        assertThat(history.points().getFirst().price()).isEqualByComparingTo("103");
    }

    @Test
    void todaysClose_isReplacedByTheCurrentPrice() {
        NavigableMap<LocalDate, BigDecimal> withToday = closes();
        withToday.put(day(10, 2), new BigDecimal("109")); // la fuente ya publicó el cierre de hoy

        PriceHistory history = build(HistoryRange.WEEK, withToday, day(10, 2));

        assertThat(history.points()).extracting(PricePoint::price).doesNotContain(new BigDecimal("109"));
        assertThat(history.points().getLast().price()).isEqualByComparingTo("110");
    }

    @Test
    void day_isThePreviousCloseAndTheCurrentPrice() {
        // Hoy lunes 5/10: el cierre anterior es el del jueves 1/10 (el último que hay).
        PriceHistory history = build(HistoryRange.DAY, closes(), day(10, 5));

        assertThat(history.points()).extracting(PricePoint::price).extracting(BigDecimal::toPlainString)
                .containsExactly("107", "110");
        assertThat(history.changePercent()).isEqualByComparingTo("2.80");
    }

    @Test
    void withoutCloses_thereIsOnlyTheCurrentPrice_andNoChange() {
        PriceHistory history = build(HistoryRange.WEEK, new TreeMap<>(), day(10, 2));

        assertThat(history.points()).hasSize(1);
        assertThat(history.changePercent()).isNull();
    }

    @Test
    void withFixedPrices_theCurrentPointUsesNow() {
        PriceQuote withoutTime = new PriceQuote(new BigDecimal("110"), null, null);

        PriceHistory history = BymaHistories.build(GGAL, HistoryRange.DAY, closes(), withoutTime, day(10, 2), ARGENTINA, NOW);

        assertThat(history.points().getLast().time()).isEqualTo(NOW);
    }

    private static PriceHistory build(HistoryRange range, NavigableMap<LocalDate, BigDecimal> closes, LocalDate today) {
        return BymaHistories.build(GGAL, range, closes, LIVE, today, ARGENTINA, NOW);
    }

    private static LocalDate day(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
