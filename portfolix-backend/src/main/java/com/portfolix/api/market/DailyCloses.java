package com.portfolix.api.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Cierres diarios de un activo, en su moneda. Solo tiene los días que operó: un fin de semana o
 * feriado se valoriza con el último cierre anterior, igual que el dólar.
 */
public final class DailyCloses {

    private final NavigableMap<LocalDate, BigDecimal> closes;

    private DailyCloses(NavigableMap<LocalDate, BigDecimal> closes) {
        this.closes = closes;
    }

    public static DailyCloses of(NavigableMap<LocalDate, BigDecimal> closes) {
        return new DailyCloses(Collections.unmodifiableNavigableMap(closes));
    }

    /** El mismo precio en cualquier fecha (stablecoins, precios fijos). */
    public static DailyCloses constant(BigDecimal price) {
        return of(new TreeMap<>(Map.of(LocalDate.MIN, price)));
    }

    /** Último cierre hasta esa fecha; vacío si el activo todavía no cotizaba (o la fuente no lo tiene). */
    public Optional<BigDecimal> on(LocalDate date) {
        return Optional.ofNullable(closes.floorEntry(date)).map(Map.Entry::getValue);
    }
}
