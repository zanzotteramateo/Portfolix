package com.portfolix.api.market;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Executor;

/**
 * Precios y dólar reales: Data912 (acciones y CEDEARs), Binance (cripto), DolarApi (dólar actual)
 * y ArgentinaDatos (dólar histórico).
 * <p>
 * Cada fuente se guarda en un caché de Caffeine en "modo refresco" ({@code refreshAfterWrite} sin vencimiento):
 * <ul>
 *   <li>cuando un valor queda viejo, se sigue sirviendo mientras se pide uno nuevo en segundo plano:
 *   el usuario nunca espera a una API externa;</li>
 *   <li>si el pedido nuevo falla, el valor viejo se conserva: siempre se usa el último conocido;</li>
 *   <li>solo si una fuente nunca respondió desde que arrancó la app, se responde 503.</li>
 * </ul>
 * Por eso no se usa {@code @Cacheable}: solo sabe si un valor está o no, no puede servir el viejo
 * mientras actualiza ni conservarlo si la actualización falla.
 */
@Component
@ConditionalOnProperty(name = "portfolix.market.provider", havingValue = "live")
class LiveMarketData implements PriceProvider, FxRateProvider {

    static final String UNAVAILABLE_MESSAGE = "No pudimos obtener las cotizaciones. Probá de nuevo en unos minutos";

    private static final Logger log = LoggerFactory.getLogger(LiveMarketData.class);
    /** Clave única para los cachés que guardan un solo valor (el dólar actual, la serie histórica). */
    private static final String SINGLE = "current";
    /** Binance cotiza todo contra USDT, así que USDT no tiene par propio: vale 1. */
    private static final String USDT = "USDT";

    private final DolarApiClient dolarApi;
    private final ArgentinaDatosClient argentinaDatos;
    private final BinanceClient binance;
    private final Data912Client data912;
    private final AssetService assetService;
    private final FxRateType fxType;
    private final Clock clock;

    private final LoadingCache<AssetType, Map<String, PriceQuote>> prices;
    private final LoadingCache<String, FxQuote> currentFx;
    private final LoadingCache<String, NavigableMap<LocalDate, BigDecimal>> fxHistory;

    @Autowired
    LiveMarketData(DolarApiClient dolarApi, ArgentinaDatosClient argentinaDatos, BinanceClient binance,
                   Data912Client data912, AssetService assetService, MarketProperties properties, Clock clock,
                   @Qualifier("marketExecutor") Executor marketExecutor) {
        this(dolarApi, argentinaDatos, binance, data912, assetService, properties, clock,
                Ticker.systemTicker(), marketExecutor);
    }

    /** Para tests: un reloj de Caffeine controlable y un executor que refresca en el mismo hilo. */
    LiveMarketData(DolarApiClient dolarApi, ArgentinaDatosClient argentinaDatos, BinanceClient binance,
                   Data912Client data912, AssetService assetService, MarketProperties properties, Clock clock,
                   Ticker ticker, Executor executor) {
        this.dolarApi = dolarApi;
        this.argentinaDatos = argentinaDatos;
        this.binance = binance;
        this.data912 = data912;
        this.assetService = assetService;
        this.fxType = properties.fxType();
        this.clock = clock;

        MarketProperties.Refresh refresh = properties.refresh();
        this.prices = cache(refresh.prices(), ticker, executor, new CacheLoader<>() {
            @Override
            public Map<String, PriceQuote> load(AssetType type) {
                return fetchPrices(type);
            }

            /** Un símbolo que no vino en la respuesta nueva conserva su último precio. */
            @Override
            public Map<String, PriceQuote> reload(AssetType type, Map<String, PriceQuote> previous) {
                Map<String, PriceQuote> merged = new HashMap<>(previous);
                merged.putAll(fetchPrices(type));
                return merged;
            }
        });
        this.currentFx = cache(refresh.fx(), ticker, executor, key -> fetchCurrentFx());
        this.fxHistory = cache(refresh.fxHistory(), ticker, executor, key -> fetchFxHistory());
    }

    /** Al arrancar se piden todos los valores en segundo plano, así el primer usuario no espera. */
    @EventListener(ApplicationReadyEvent.class)
    void warmUp() {
        prices.refreshAll(List.of(AssetType.values()));
        currentFx.refresh(SINGLE);
        fxHistory.refresh(SINGLE);
    }

    @Override
    public PriceQuote quote(Asset asset) {
        PriceQuote quote = get(prices, asset.getType(), "precios de " + asset.getType()).get(asset.getSymbol());
        if (quote == null) {
            log.warn("La fuente de {} no tiene cotización para {}", asset.getType(), asset.getSymbol());
            throw new ServiceUnavailableException(
                    "No pudimos obtener la cotización de %s. Probá de nuevo en unos minutos".formatted(asset.getSymbol()));
        }
        return quote;
    }

    @Override
    public FxQuote currentQuote() {
        return get(currentFx, SINGLE, "dólar actual");
    }

    @Override
    public BigDecimal rateOn(LocalDate date) {
        NavigableMap<LocalDate, BigDecimal> history = get(fxHistory, SINGLE, "dólar histórico");
        // Fin de semana o feriado: la del último día anterior con cotización.
        Map.Entry<LocalDate, BigDecimal> entry = history.floorEntry(date);
        return (entry != null ? entry : history.firstEntry()).getValue();
    }

    private Map<String, PriceQuote> fetchPrices(AssetType type) {
        Instant now = clock.instant();
        return switch (type) {
            case STOCK -> fromData912(data912.stocks(), now);
            case CEDEAR -> fromData912(data912.cedears(), now);
            case CRYPTO -> fromBinance(now);
        };
    }

    private static Map<String, PriceQuote> fromData912(List<Data912Client.Quote> quotes, Instant now) {
        Map<String, PriceQuote> result = new HashMap<>();
        for (Data912Client.Quote quote : quotes) {
            // Sin precio (o en 0) es que no operó: se ignora y queda el último conocido.
            if (quote.symbol() != null && quote.price() != null && quote.price().signum() > 0) {
                result.put(quote.symbol(), new PriceQuote(quote.price(), quote.pctChange(), now));
            }
        }
        return result;
    }

    private Map<String, PriceQuote> fromBinance(Instant now) {
        List<String> symbols = assetService.symbolsOfType(AssetType.CRYPTO);
        Map<String, PriceQuote> result = new HashMap<>();
        if (symbols.contains(USDT)) {
            result.put(USDT, new PriceQuote(BigDecimal.ONE, BigDecimal.ZERO, now));
        }
        List<String> pairs = symbols.stream().filter(symbol -> !symbol.equals(USDT)).map(symbol -> symbol + USDT).toList();
        if (!pairs.isEmpty()) {
            for (BinanceClient.Ticker ticker : binance.tickers24h(pairs)) {
                String symbol = ticker.symbol().substring(0, ticker.symbol().length() - USDT.length());
                result.put(symbol, new PriceQuote(ticker.lastPrice(), ticker.priceChangePercent(), now));
            }
        }
        return result;
    }

    private FxQuote fetchCurrentFx() {
        DolarApiClient.Response response = dolarApi.current(fxType);
        Instant updatedAt = response.fechaActualizacion() != null ? response.fechaActualizacion() : clock.instant();
        return new FxQuote(fxType, response.compra(), response.venta(), updatedAt);
    }

    private NavigableMap<LocalDate, BigDecimal> fetchFxHistory() {
        TreeMap<LocalDate, BigDecimal> history = new TreeMap<>();
        for (ArgentinaDatosClient.Entry entry : argentinaDatos.history(fxType)) {
            if (entry.fecha() != null && entry.venta() != null) {
                history.put(entry.fecha(), entry.venta()); // venta, igual que el dólar actual
            }
        }
        if (history.isEmpty()) {
            throw new IllegalStateException("ArgentinaDatos devolvió un histórico vacío");
        }
        return Collections.unmodifiableNavigableMap(history);
    }

    private static <K, V> LoadingCache<K, V> cache(Duration refreshAfter, Ticker ticker, Executor executor,
                                                   CacheLoader<K, V> loader) {
        return Caffeine.newBuilder()
                .refreshAfterWrite(refreshAfter) // sin expireAfterWrite: el último valor nunca se descarta
                .ticker(ticker)
                .executor(executor)
                .build(loader);
    }

    /** La primera carga es la única que puede fallar hacia afuera: después siempre hay un valor conocido. */
    private static <K, V> V get(LoadingCache<K, V> cache, K key, String description) {
        try {
            return cache.get(key);
        } catch (RuntimeException ex) {
            log.warn("No se pudo obtener {} y no hay un valor anterior: {}", description, ex.getMessage());
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }
}
