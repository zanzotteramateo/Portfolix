package com.portfolix.api.market;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.BusinessCalendar;
import com.portfolix.api.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Executor;

/**
 * Historial de precios real: velas de Binance para cripto y cierres diarios de Data912 para BYMA.
 * Mismo esquema de caché que LiveMarketData (último valor conocido, refresco en segundo plano).
 */
@Component
@ConditionalOnProperty(name = "portfolix.market.provider", havingValue = "live")
class LiveMarketHistory implements PriceHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketHistory.class);
    /** De cada histórico diario de BYMA (años de datos) se guardan solo los últimos días. */
    private static final int DAILY_CLOSES_KEPT = 30;
    private static final String USDT = "USDT";

    private record DailyKey(AssetType type, String symbol) {
    }

    private final BinanceClient binance;
    private final Data912Client data912;
    private final PriceProvider priceProvider;
    private final BusinessCalendar calendar;
    private final Clock clock;

    /** Cripto, por símbolo: 96 velas de 15 min (24 h) y 168 velas de 1 h (7 días). */
    private final LoadingCache<String, List<PricePoint>> cryptoDay;
    private final LoadingCache<String, List<PricePoint>> cryptoWeek;
    /** BYMA: cierres diarios de los últimos días. */
    private final LoadingCache<DailyKey, NavigableMap<LocalDate, BigDecimal>> dailyCloses;

    @Autowired
    LiveMarketHistory(BinanceClient binance, Data912Client data912, PriceProvider priceProvider,
                      BusinessCalendar calendar, MarketProperties properties, Clock clock,
                      @Qualifier("marketExecutor") Executor marketExecutor) {
        this(binance, data912, priceProvider, calendar, properties, clock, Ticker.systemTicker(), marketExecutor);
    }

    LiveMarketHistory(BinanceClient binance, Data912Client data912, PriceProvider priceProvider,
                      BusinessCalendar calendar, MarketProperties properties, Clock clock,
                      Ticker ticker, Executor executor) {
        this.binance = binance;
        this.data912 = data912;
        this.priceProvider = priceProvider;
        this.calendar = calendar;
        this.clock = clock;

        MarketProperties.Refresh refresh = properties.refresh();
        this.cryptoDay = cache(refresh.cryptoDayHistory(), ticker, executor, symbol -> fetchKlines(symbol, "15m", 96));
        this.cryptoWeek = cache(refresh.cryptoWeekHistory(), ticker, executor, symbol -> fetchKlines(symbol, "1h", 168));
        this.dailyCloses = cache(refresh.dailyCloses(), ticker, executor, this::fetchDailyCloses);
    }

    @Override
    public PriceHistory history(Asset asset, HistoryRange range) {
        try {
            return build(asset, range, true).orElseThrow();
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.warn("No se pudo obtener el historial {} de {}: {}", range.param(), asset.getSymbol(), ex.getMessage());
            throw new ServiceUnavailableException(LiveMarketData.UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public Optional<PriceHistory> cachedHistory(Asset asset, HistoryRange range) {
        try {
            return build(asset, range, false);
        } catch (RuntimeException ex) {
            return Optional.empty(); // decorativo: sin tendencia antes que sin tabla
        }
    }

    /**
     * @param wait si es {@code false} y el historial no está cargado, lo pide en segundo plano y devuelve vacío
     */
    private Optional<PriceHistory> build(Asset asset, HistoryRange range, boolean wait) {
        if (asset.getType() == AssetType.CRYPTO) {
            LoadingCache<String, List<PricePoint>> cache = range == HistoryRange.DAY ? cryptoDay : cryptoWeek;
            return read(cache, asset.getSymbol(), wait)
                    .map(points -> PriceHistory.of(asset.getSymbol(), asset.getCurrency(), range, points));
        }
        return read(dailyCloses, new DailyKey(asset.getType(), asset.getSymbol()), wait)
                .map(closes -> BymaHistories.build(asset, range, closes, priceProvider.quote(asset),
                        calendar.today(), calendar.zone(), clock.instant()));
    }

    private static <K, V> Optional<V> read(LoadingCache<K, V> cache, K key, boolean wait) {
        if (wait) {
            return Optional.of(cache.get(key));
        }
        V value = cache.getIfPresent(key);
        if (value == null) {
            cache.refresh(key); // se carga en segundo plano; el próximo pedido ya lo tiene
        }
        return Optional.ofNullable(value);
    }

    private List<PricePoint> fetchKlines(String symbol, String interval, int limit) {
        if (symbol.equals(USDT)) {
            return List.of(new PricePoint(clock.instant(), BigDecimal.ONE)); // USDT es la unidad de Binance
        }
        List<BinanceClient.Kline> klines = binance.klines(symbol + USDT, interval, limit);
        if (klines.isEmpty()) {
            throw new IllegalStateException("Binance no devolvió velas para " + symbol);
        }
        // Primer punto: la apertura de la primera vela (el precio al inicio del rango); después, cada cierre.
        List<PricePoint> points = new ArrayList<>(klines.size() + 1);
        points.add(new PricePoint(klines.getFirst().openTime(), klines.getFirst().open()));
        klines.forEach(kline -> points.add(new PricePoint(kline.closeTime(), kline.close())));
        return points;
    }

    private NavigableMap<LocalDate, BigDecimal> fetchDailyCloses(DailyKey key) {
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (Data912Client.Candle candle : data912.history(key.type(), key.symbol())) {
            if (candle.date() != null && candle.close() != null && candle.close().signum() > 0) {
                closes.put(candle.date(), candle.close());
            }
        }
        if (closes.isEmpty()) {
            throw new IllegalStateException("Data912 no devolvió cierres para " + key.symbol());
        }
        LocalDate oldestKept = closes.lastKey().minusDays(DAILY_CLOSES_KEPT);
        return Collections.unmodifiableNavigableMap(new TreeMap<>(closes.tailMap(oldestKept, true)));
    }

    private static <K, V> LoadingCache<K, V> cache(Duration refreshAfter, Ticker ticker, Executor executor,
                                                   CacheLoader<K, V> loader) {
        return Caffeine.newBuilder()
                .refreshAfterWrite(refreshAfter) // sin vencimiento: si la API falla, queda el último conocido
                .ticker(ticker)
                .executor(executor)
                .build(loader);
    }
}
