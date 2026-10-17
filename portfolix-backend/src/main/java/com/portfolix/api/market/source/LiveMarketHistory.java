package com.portfolix.api.market.source;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.BusinessCalendar;
import com.portfolix.api.common.exception.ServiceUnavailableException;
import com.portfolix.api.market.ClosesRequest;
import com.portfolix.api.market.DailyCloses;
import com.portfolix.api.market.HistoryRange;
import com.portfolix.api.market.PriceHistory;
import com.portfolix.api.market.PriceHistoryProvider;
import com.portfolix.api.market.PricePoint;
import com.portfolix.api.market.PriceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * Historial de precios real. Gráficos de tendencia: CoinGecko para cripto y cierres diarios de Data912
 * para BYMA. Cierres diarios del análisis: los mismos de Data912 y, para cripto, Coinbase.
 * Mismo esquema de caché que LiveMarketData (último valor conocido, refresco en segundo plano).
 */
@Component
@ConditionalOnProperty(name = "portfolix.market.provider", havingValue = "live")
class LiveMarketHistory implements PriceHistoryProvider {

    private static final Logger log = LoggerFactory.getLogger(LiveMarketHistory.class);
    /**
     * De cada histórico diario de BYMA (desde 2001) se guardan los cierres desde esta fecha: alcanzan para
     * el análisis y son ~1.700 por activo. Antes de esa fecha, el análisis usa el precio de las operaciones.
     */
    static final LocalDate BYMA_CLOSES_SINCE = LocalDate.of(2020, 1, 1);
    private static final String USDT = "USDT";
    /** Sus cierres son 1 fijo (Coinbase no tiene USDC-USD: para Coinbase, USDC es el dólar). */
    private static final Set<String> STABLECOINS = Set.of(USDT, "USDC");

    private record DailyKey(AssetType type, String symbol) {
    }

    /**
     * Medio año de cierres de una cripto: un solo pedido a Coinbase (hasta 184 velas; el máximo es 300).
     *
     * @param start 1 de enero o 1 de julio
     */
    record Semester(String symbol, LocalDate start) {

        static Semester of(String symbol, LocalDate date) {
            return new Semester(symbol, LocalDate.of(date.getYear(), date.getMonthValue() <= 6 ? 1 : 7, 1));
        }

        LocalDate end() {
            return start.plusMonths(6).minusDays(1);
        }

        Semester next() {
            return new Semester(symbol, start.plusMonths(6));
        }
    }

    /**
     * @param loadedOn día (en Argentina) en que se pidió: si es posterior al fin del semestre, ya estaba completo
     */
    private record SemesterCloses(NavigableMap<LocalDate, BigDecimal> closes, LocalDate loadedOn) {
    }

    private final CoinGeckoClient coinGecko;
    private final Data912Client data912;
    private final CoinbaseClient coinbase;
    private final PriceProvider priceProvider;
    private final BusinessCalendar calendar;
    private final Clock clock;
    private final Executor executor;

    /** Cripto, por símbolo: ~día (CoinGecko ajusta solo la densidad) y 7 días. */
    private final LoadingCache<String, List<PricePoint>> cryptoDay;
    private final LoadingCache<String, List<PricePoint>> cryptoWeek;
    /** BYMA: cierres diarios desde {@link #BYMA_CLOSES_SINCE}. Los usan los gráficos y el análisis. */
    private final LoadingCache<DailyKey, NavigableMap<LocalDate, BigDecimal>> bymaCloses;
    /** Cripto: cierres diarios de Coinbase, por semestre. */
    private final LoadingCache<Semester, SemesterCloses> cryptoCloses;

    @Autowired
    LiveMarketHistory(CoinGeckoClient coinGecko, Data912Client data912, CoinbaseClient coinbase,
                      PriceProvider priceProvider, BusinessCalendar calendar, MarketProperties properties,
                      Clock clock, @Qualifier("marketExecutor") Executor marketExecutor) {
        this(coinGecko, data912, coinbase, priceProvider, calendar, properties, clock, Ticker.systemTicker(),
                marketExecutor);
    }

    LiveMarketHistory(CoinGeckoClient coinGecko, Data912Client data912, CoinbaseClient coinbase,
                      PriceProvider priceProvider, BusinessCalendar calendar, MarketProperties properties,
                      Clock clock, Ticker ticker, Executor executor) {
        this.coinGecko = coinGecko;
        this.data912 = data912;
        this.coinbase = coinbase;
        this.priceProvider = priceProvider;
        this.calendar = calendar;
        this.clock = clock;
        this.executor = executor;

        MarketProperties.Refresh refresh = properties.refresh();
        this.cryptoDay = cache(refresh.cryptoDayHistory(), ticker, executor, symbol -> fetchMarketChart(symbol, 1));
        this.cryptoWeek = cache(refresh.cryptoWeekHistory(), ticker, executor, symbol -> fetchMarketChart(symbol, 7));
        this.bymaCloses = cache(refresh.dailyCloses(), ticker, executor, this::fetchBymaCloses);
        this.cryptoCloses = cache(refresh.dailyCloses(), ticker, executor, new CacheLoader<>() {
            @Override
            public SemesterCloses load(Semester semester) {
                return fetchSemester(semester);
            }

            /** Un semestre que ya estaba terminado cuando se pidió no cambia más: no se vuelve a pedir. */
            @Override
            public SemesterCloses reload(Semester semester, SemesterCloses previous) {
                return previous.loadedOn().isAfter(semester.end()) ? previous : fetchSemester(semester);
            }
        });
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
        return read(bymaCloses, new DailyKey(asset.getType(), asset.getSymbol()), wait)
                .map(closes -> BymaHistories.build(asset, range, closes, priceProvider.quote(asset),
                        calendar.today(), calendar.zone(), clock.instant()));
    }

    @Override
    public Map<String, DailyCloses> dailyCloses(Collection<ClosesRequest> requests) {
        // Cada activo en su hilo: el histórico de Data912 tarda 2-4 s por activo. Los semestres de una cripto,
        // en cambio, se piden de a uno dentro de su hilo, para no pasarse del límite por segundo de Coinbase.
        Map<String, CompletableFuture<DailyCloses>> pending = new LinkedHashMap<>();
        for (ClosesRequest request : requests) {
            pending.put(request.asset().getSymbol(), CompletableFuture.supplyAsync(() -> closes(request), executor));
        }
        Map<String, DailyCloses> result = new HashMap<>();
        pending.forEach((symbol, future) -> result.put(symbol, await(symbol, future)));
        return result;
    }

    private DailyCloses closes(ClosesRequest request) {
        Asset asset = request.asset();
        if (asset.getType() != AssetType.CRYPTO) {
            return DailyCloses.of(bymaCloses.get(new DailyKey(asset.getType(), asset.getSymbol())));
        }
        if (STABLECOINS.contains(asset.getSymbol())) {
            return DailyCloses.constant(BigDecimal.ONE);
        }
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (Semester semester = Semester.of(asset.getSymbol(), request.from());
             !semester.start().isAfter(request.to());
             semester = semester.next()) {
            closes.putAll(cryptoCloses.get(semester).closes());
        }
        return DailyCloses.of(closes);
    }

    private static DailyCloses await(String symbol, CompletableFuture<DailyCloses> future) {
        try {
            return future.join();
        } catch (CompletionException ex) {
            log.warn("No se pudieron obtener los cierres diarios de {}: {}", symbol, ex.getCause().getMessage());
            throw new ServiceUnavailableException(LiveMarketData.UNAVAILABLE_MESSAGE);
        }
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

    private List<PricePoint> fetchMarketChart(String symbol, int days) {
        if (symbol.equals(USDT)) {
            return List.of(new PricePoint(clock.instant(), BigDecimal.ONE)); // fijo, igual que en LiveMarketData
        }
        List<List<BigDecimal>> rows = coinGecko.marketChart(CoinGeckoIds.of(symbol), days);
        if (rows.isEmpty()) {
            throw new IllegalStateException("CoinGecko no devolvió historial para " + symbol);
        }
        return rows.stream()
                .map(row -> new PricePoint(Instant.ofEpochMilli(row.get(0).longValueExact()), row.get(1)))
                .toList();
    }

    private NavigableMap<LocalDate, BigDecimal> fetchBymaCloses(DailyKey key) {
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (Data912Client.Candle candle : data912.history(key.type(), key.symbol())) {
            if (candle.date() != null && !candle.date().isBefore(BYMA_CLOSES_SINCE)
                    && candle.close() != null && candle.close().signum() > 0) {
                closes.put(candle.date(), candle.close());
            }
        }
        if (closes.isEmpty()) {
            throw new IllegalStateException("Data912 no devolvió cierres para " + key.symbol());
        }
        return Collections.unmodifiableNavigableMap(closes);
    }

    private SemesterCloses fetchSemester(Semester semester) {
        LocalDate today = calendar.today();
        LocalDate end = semester.end().isBefore(today) ? semester.end() : today;
        TreeMap<LocalDate, BigDecimal> closes = new TreeMap<>();
        for (CoinbaseClient.DailyClose candle : coinbase.dailyCloses(semester.symbol(), semester.start(), end)) {
            if (candle.close() != null && candle.close().signum() > 0) {
                closes.put(candle.date(), candle.close());
            }
        }
        // Vacío no es un error: la cripto todavía no cotizaba en Coinbase (ej.: BNB antes del 22/10/2025).
        return new SemesterCloses(Collections.unmodifiableNavigableMap(closes), today);
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
