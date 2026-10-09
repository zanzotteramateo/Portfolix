package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Arma los gráficos de acciones y CEDEARs a partir de los cierres diarios más el precio actual.
 * BYMA no tiene datos dentro del día en las fuentes gratuitas: el gráfico de 24 h es
 * "cierre anterior → precio actual" (dos puntos) y el de 7 días, un punto por día hábil.
 * Clase pura, para probar los casos de fines de semana y feriados sin APIs.
 */
final class BymaHistories {

    /** Hora de cierre de BYMA: la que se le asigna a cada cierre diario. */
    static final LocalTime MARKET_CLOSE = LocalTime.of(17, 0);

    private BymaHistories() {
    }

    /**
     * @param closes cierres diarios por fecha
     * @param today  hoy en Argentina: el cierre de hoy (si ya está) se reemplaza por el precio actual
     */
    static PriceHistory build(Asset asset, HistoryRange range, NavigableMap<LocalDate, BigDecimal> closes,
                              PriceQuote live, LocalDate today, ZoneId zone, Instant now) {
        List<PricePoint> points = new ArrayList<>();
        if (range == HistoryRange.DAY) {
            // Último cierre de un día anterior a hoy (el viernes, si hoy es lunes).
            Map.Entry<LocalDate, BigDecimal> previous = closes.lowerEntry(today);
            if (previous != null) {
                points.add(point(previous, zone));
            }
        } else {
            LocalDate start = today.minusDays(range.duration().toDays());
            // Base: el último cierre hasta el inicio del rango (si ese día fue feriado, el anterior).
            Map.Entry<LocalDate, BigDecimal> base = closes.floorEntry(start);
            if (base != null) {
                points.add(point(base, zone));
            }
            // Los cierres de después de la base y antes de hoy.
            closes.subMap(start, false, today, false).entrySet()
                    .forEach(entry -> points.add(point(entry, zone)));
        }
        points.add(new PricePoint(live.updatedAt() != null ? live.updatedAt() : now, live.price()));
        return PriceHistory.of(asset.getSymbol(), asset.getCurrency(), range, points);
    }

    private static PricePoint point(Map.Entry<LocalDate, BigDecimal> close, ZoneId zone) {
        return new PricePoint(close.getKey().atTime(MARKET_CLOSE).atZone(zone).toInstant(), close.getValue());
    }
}
