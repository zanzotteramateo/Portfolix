package com.portfolix.api.market;

import com.portfolix.api.common.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PriceHistoryTest {

    @Test
    void change_isBetweenTheFirstAndTheLastPoint() {
        PriceHistory history = history(List.of("100", "90", "125"));

        assertThat(history.changePercent()).isEqualByComparingTo("25");
    }

    @Test
    void withASinglePoint_thereIsNoChange() {
        assertThat(history(List.of("100")).changePercent()).isNull();
    }

    @Test
    void sparkline_samplesLongSeries_keepingTheFirstAndLastPrice() {
        // Una semana de velas de 1 h: 169 puntos (0, 1, 2, ..., 168).
        List<String> prices = IntStream.rangeClosed(0, 168).mapToObj(String::valueOf).toList();

        List<BigDecimal> sparkline = history(prices).sparkline(28);

        assertThat(sparkline).hasSize(28);
        assertThat(sparkline.getFirst()).isEqualByComparingTo("0");
        assertThat(sparkline.getLast()).isEqualByComparingTo("168");
        assertThat(sparkline).isSortedAccordingTo(BigDecimal::compareTo); // repartidos en orden, sin repetir
        assertThat(sparkline).doesNotHaveDuplicates();
    }

    @Test
    void sparkline_keepsShortSeriesAsTheyAre() {
        assertThat(history(List.of("1", "2", "3")).sparkline(28)).extracting(BigDecimal::toPlainString)
                .containsExactly("1", "2", "3");
    }

    private static PriceHistory history(List<String> prices) {
        Instant start = Instant.parse("2026-09-25T00:00:00Z");
        List<PricePoint> points = IntStream.range(0, prices.size())
                .mapToObj(i -> new PricePoint(start.plusSeconds(3600L * i), new BigDecimal(prices.get(i))))
                .toList();
        return PriceHistory.of("BTC", Currency.USD, HistoryRange.WEEK, points);
    }
}
