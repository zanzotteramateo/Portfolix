package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Precios y dólar fijos, leídos de market/fixed-prices.yml. Sirve para los tests y para desarrollar
 * sin internet: los números no cambian entre una corrida y otra.
 */
@Component
@ConditionalOnProperty(name = "portfolix.market.provider", havingValue = "fixed")
public class FixedMarketData implements PriceProvider, FxRateProvider, PriceHistoryProvider {

    private final Map<String, BigDecimal> prices = new HashMap<>();
    private final FxQuote fx;
    private final Clock clock;

    public FixedMarketData(MarketProperties properties, Clock clock) {
        properties.fixed().prices().forEach((symbol, price) -> prices.put(symbol.toUpperCase(Locale.ROOT), price));
        BigDecimal usdRate = properties.fixed().usdRate();
        this.fx = new FxQuote(properties.fxType(), usdRate, usdRate, null);
        this.clock = clock;
    }

    /** Línea plana: el precio fijo al inicio y al final del rango (variación 0). */
    @Override
    public PriceHistory history(Asset asset, HistoryRange range) {
        BigDecimal price = quote(asset).price();
        Instant now = clock.instant();
        List<PricePoint> points = List.of(new PricePoint(now.minus(range.duration()), price), new PricePoint(now, price));
        return PriceHistory.of(asset.getSymbol(), asset.getCurrency(), range, points);
    }

    @Override
    public Optional<PriceHistory> cachedHistory(Asset asset, HistoryRange range) {
        return Optional.of(history(asset, range));
    }

    @Override
    public PriceQuote quote(Asset asset) {
        BigDecimal price = prices.get(asset.getSymbol());
        if (price == null) {
            throw new IllegalStateException("No hay precio fijo para %s en market/fixed-prices.yml".formatted(asset.getSymbol()));
        }
        return new PriceQuote(price, null, null);
    }

    @Override
    public FxQuote currentQuote() {
        return fx;
    }

    /** Sin histórico: todas las fechas usan la misma cotización. */
    @Override
    public BigDecimal rateOn(LocalDate date) {
        return fx.rate();
    }
}
