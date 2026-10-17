package com.portfolix.api.performance;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.BusinessCalendar;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.Rounding;
import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.performance.PerformanceCalculator.AssetPeriod;
import com.portfolix.api.performance.PerformanceCalculator.Flow;
import com.portfolix.api.performance.dto.PerformanceResponse;
import com.portfolix.api.performance.dto.PerformanceResponse.AssetPerformance;
import com.portfolix.api.performance.dto.PerformanceResponse.ValuePoint;
import com.portfolix.api.market.ClosesRequest;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.market.DailyCloses;
import com.portfolix.api.market.PriceHistoryProvider;
import com.portfolix.api.market.PriceProvider;
import com.portfolix.api.market.PriceQuote;
import com.portfolix.api.portfolio.PortfolioService;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionService;
import com.portfolix.api.transaction.TransactionType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Análisis de un período: cuánto ganaste o perdiste, en total y por activo (ver {@link PerformanceCalculator}).
 */
@Service
public class PerformanceService {

    static final String FUTURE_END_MESSAGE = "La fecha de fin no puede ser futura";
    static final String INVERTED_RANGE_MESSAGE = "La fecha de inicio no puede ser posterior a la de fin";

    private static final Comparator<AssetPerformance> BY_PNL_DESC =
            Comparator.comparing(AssetPerformance::pnl).reversed().thenComparing(AssetPerformance::symbol);

    private final TransactionService transactionService;
    private final PortfolioService portfolioService;
    private final PriceProvider priceProvider;
    private final PriceHistoryProvider priceHistoryProvider;
    private final CurrencyConverter converter;
    private final BusinessCalendar calendar;

    public PerformanceService(TransactionService transactionService, PortfolioService portfolioService,
                              PriceProvider priceProvider, PriceHistoryProvider priceHistoryProvider,
                              CurrencyConverter converter, BusinessCalendar calendar) {
        this.transactionService = transactionService;
        this.portfolioService = portfolioService;
        this.priceProvider = priceProvider;
        this.priceHistoryProvider = priceHistoryProvider;
        this.converter = converter;
        this.calendar = calendar;
    }

    /**
     * Sin {@code @Transactional} a propósito: las transacciones se leen en su propia transacción (ya vienen
     * con el activo cargado) y después se espera a las fuentes de precios sin tener tomada una conexión a la base.
     *
     * @param portfolioId {@code null}: todos los portafolios
     * @param type        {@code null}: todos los tipos de activo
     * @param from        {@code null}: desde la primera operación
     * @param to          {@code null}: hasta hoy
     */
    public PerformanceResponse performance(Long userId, Long portfolioId, AssetType type, LocalDate from,
                                           LocalDate to, Currency currency) {
        LocalDate today = calendar.today();
        LocalDate end = to != null ? to : today;
        if (end.isAfter(today)) {
            throw new BusinessException("to", FUTURE_END_MESSAGE);
        }
        if (from != null && from.isAfter(end)) {
            throw new BusinessException("from", INVERTED_RANGE_MESSAGE);
        }
        if (portfolioId != null) {
            portfolioService.getOwned(userId, portfolioId); // 404 si no existe o es de otro usuario
        }

        List<Transaction> transactions = transactionService.findForPositions(userId, portfolioId, null).stream()
                .filter(tx -> type == null || tx.getAsset().getType() == type)
                .filter(tx -> !tx.getTradeDate().isAfter(end))
                .toList();
        // Sin inicio: desde la primera operación, así el valor inicial es 0 (y el resultado, el del dashboard).
        LocalDate start = from != null ? from : transactions.stream()
                .map(Transaction::getTradeDate)
                .min(Comparator.naturalOrder())
                .orElse(end);

        // Por activo, con las transacciones de todos los portafolios juntas: en un período, las cantidades,
        // compras y ventas se suman sin más (no hay precio promedio de por medio).
        List<List<Transaction>> held = transactions.stream()
                .collect(groupingBy(tx -> tx.getAsset().getId(), LinkedHashMap::new, toList()))
                .values().stream()
                .filter(assetTransactions -> heldDuring(assetTransactions, start))
                .toList();
        Map<String, DailyCloses> closes = dailyCloses(held, start, end, today);
        List<AssetResult> results = held.stream()
                .map(assetTransactions -> evaluate(assetTransactions, start, end, today, closes, currency))
                .toList();

        return response(results, start, end, currency);
    }

    /** Se tenía algo al empezar o se operó durante el período (las transacciones llegan hasta el final). */
    private static boolean heldDuring(List<Transaction> transactions, LocalDate start) {
        BigDecimal quantityAtStart = BigDecimal.ZERO;
        for (Transaction tx : transactions) {
            if (!tx.getTradeDate().isBefore(start)) {
                return true;
            }
            quantityAtStart = tx.getType() == TransactionType.BUY
                    ? quantityAtStart.add(tx.getQuantity())
                    : quantityAtStart.subtract(tx.getQuantity());
        }
        return quantityAtStart.signum() > 0;
    }

    /**
     * Cierres de los días anteriores a hoy en que hace falta precio: desde el día anterior al inicio (o la
     * primera compra, si es posterior). Hoy se valoriza con el precio actual, como el dashboard.
     */
    private Map<String, DailyCloses> dailyCloses(List<List<Transaction>> held, LocalDate start, LocalDate end,
                                                 LocalDate today) {
        LocalDate lastClose = end.isBefore(today) ? end : today.minusDays(1);
        List<ClosesRequest> requests = new ArrayList<>();
        for (List<Transaction> transactions : held) {
            LocalDate firstBuy = transactions.getFirst().getTradeDate();
            LocalDate dayBefore = start.minusDays(1);
            LocalDate firstHeld = firstBuy.isAfter(dayBefore) ? firstBuy : dayBefore;
            if (!firstHeld.isAfter(lastClose)) {
                requests.add(new ClosesRequest(transactions.getFirst().getAsset(), firstHeld, lastClose));
            }
        }
        return requests.isEmpty() ? Map.of() : priceHistoryProvider.dailyCloses(requests);
    }

    private AssetResult evaluate(List<Transaction> transactions, LocalDate start, LocalDate end, LocalDate today,
                                 Map<String, DailyCloses> closes, Currency currency) {
        Asset asset = transactions.getFirst().getAsset();
        Optional<DailyCloses> assetCloses = Optional.ofNullable(closes.get(asset.getSymbol()));
        AtomicBoolean estimated = new AtomicBoolean();
        AtomicReference<Instant> priceUpdatedAt = new AtomicReference<>();

        Function<LocalDate, BigDecimal> unitValue = day -> {
            if (day.equals(today)) {
                PriceQuote quote = priceProvider.quote(asset);
                priceUpdatedAt.set(quote.updatedAt());
                return quote.price().multiply(converter.currentFactor(asset.getCurrency(), currency));
            }
            BigDecimal price = assetCloses.flatMap(assetClose -> assetClose.on(day)).orElseGet(() -> {
                // Sin precio de mercado ese día (ej.: todavía no cotizaba en la fuente): el de la última
                // operación propia. Existe siempre, porque solo se pide para días en que se tenía algo.
                estimated.set(true);
                return lastTradePrice(transactions, day);
            });
            return price.multiply(converter.factorOn(day, asset.getCurrency(), currency));
        };
        AssetPeriod period = PerformanceCalculator.asset(transactions, start, end, unitValue,
                day -> converter.factorOn(day, asset.getCurrency(), currency));
        return new AssetResult(asset, period, estimated.get(), priceUpdatedAt.get());
    }

    private static BigDecimal lastTradePrice(List<Transaction> transactions, LocalDate day) {
        BigDecimal price = null;
        for (Transaction tx : transactions) {
            if (tx.getTradeDate().isAfter(day)) {
                break;
            }
            price = tx.getPrice();
        }
        return Objects.requireNonNull(price, "Se pidió un precio antes de la primera operación");
    }

    private static PerformanceResponse response(List<AssetResult> results, LocalDate start, LocalDate end,
                                                Currency currency) {
        List<AssetPerformance> assets = results.stream()
                .map(result -> toRow(result, start, end))
                .sorted(BY_PNL_DESC)
                .toList();

        // Los totales son la suma de los montos ya redondeados de cada fila: así cierran con la tabla.
        BigDecimal startValue = sum(assets, AssetPerformance::startValue);
        BigDecimal endValue = sum(assets, AssetPerformance::endValue);
        BigDecimal bought = sum(assets, AssetPerformance::bought);
        BigDecimal sold = sum(assets, AssetPerformance::sold);
        BigDecimal pnl = endValue.subtract(startValue).subtract(bought).add(sold);
        List<Flow> flows = results.stream()
                .flatMap(result -> result.period().flows().stream())
                .sorted(Comparator.comparing(Flow::date))
                .toList();

        return new PerformanceResponse(
                currency,
                start,
                end,
                startValue,
                endValue,
                bought,
                sold,
                pnl,
                PerformanceCalculator.percent(start, end, startValue, endValue, flows, pnl),
                assets.stream().filter(asset -> asset.pnl().signum() > 0).max(Comparator.comparing(AssetPerformance::pnl))
                        .orElse(null),
                assets.stream().filter(asset -> asset.pnl().signum() < 0).min(Comparator.comparing(AssetPerformance::pnl))
                        .orElse(null),
                series(results, flows, start, end, startValue),
                assets,
                results.stream()
                        .map(AssetResult::priceUpdatedAt)
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null));
    }

    private static AssetPerformance toRow(AssetResult result, LocalDate start, LocalDate end) {
        AssetPeriod period = result.period();
        BigDecimal startValue = Rounding.amount(period.startValue());
        BigDecimal endValue = Rounding.amount(period.endValue());
        BigDecimal bought = Rounding.amount(period.bought());
        BigDecimal sold = Rounding.amount(period.sold());
        BigDecimal pnl = endValue.subtract(startValue).subtract(bought).add(sold);
        Asset asset = result.asset();
        return new AssetPerformance(
                asset.getSymbol(),
                asset.getName(),
                asset.getType(),
                period.startQuantity(),
                period.endQuantity(),
                startValue,
                bought,
                sold,
                endValue,
                pnl,
                PerformanceCalculator.percent(start, end, startValue, endValue, period.flows(), pnl),
                result.estimatedPrice());
    }

    /**
     * Valor y plata puesta en cada día del gráfico. El valor de cada día es la suma de los de cada activo
     * redondeados, como los totales: el primer punto es exactamente el valor inicial y el último, el final.
     */
    private static List<ValuePoint> series(List<AssetResult> results, List<Flow> flows, LocalDate start,
                                           LocalDate end, BigDecimal startValue) {
        List<ValuePoint> points = new ArrayList<>();
        BigDecimal netFlows = BigDecimal.ZERO;
        int nextFlow = 0;
        for (LocalDate day : PerformanceCalculator.chartDays(start, end)) {
            while (nextFlow < flows.size() && !flows.get(nextFlow).date().isAfter(day)) {
                Flow flow = flows.get(nextFlow++);
                netFlows = flow.type() == TransactionType.BUY ? netFlows.add(flow.amount()) : netFlows.subtract(flow.amount());
            }
            BigDecimal value = results.stream()
                    .map(result -> result.period().dailyValues().get(day))
                    .filter(Objects::nonNull)
                    .map(Rounding::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            points.add(new ValuePoint(day, value, startValue.add(Rounding.amount(netFlows))));
        }
        return points;
    }

    private static BigDecimal sum(List<AssetPerformance> assets, Function<AssetPerformance, BigDecimal> amount) {
        return assets.stream().map(amount).reduce(Rounding.amount(BigDecimal.ZERO), BigDecimal::add);
    }

    /**
     * @param priceUpdatedAt hora del precio actual usado; {@code null} si el período no llega a hoy
     */
    private record AssetResult(Asset asset, AssetPeriod period, boolean estimatedPrice, Instant priceUpdatedAt) {
    }
}
