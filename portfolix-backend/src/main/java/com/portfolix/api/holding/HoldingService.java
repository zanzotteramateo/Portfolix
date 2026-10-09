package com.portfolix.api.holding;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.Rounding;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.holding.dto.HoldingResponse;
import com.portfolix.api.holding.dto.HoldingsResponse;
import com.portfolix.api.market.CurrencyConverter;
import com.portfolix.api.market.HistoryRange;
import com.portfolix.api.market.PriceHistory;
import com.portfolix.api.market.PriceHistoryProvider;
import com.portfolix.api.market.PriceProvider;
import com.portfolix.api.market.PriceQuote;
import com.portfolix.api.portfolio.PortfolioService;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

/**
 * Posiciones del usuario: se calculan a partir de las transacciones (no tienen tabla propia).
 */
@Service
public class HoldingService {

    static final String NO_OPERATIONS_MESSAGE = "No tenés operaciones de este activo";

    private static final Comparator<HoldingResponse> BY_CURRENT_VALUE_DESC =
            Comparator.comparing(HoldingResponse::currentValue).reversed()
                    .thenComparing(HoldingResponse::symbol);

    /** Puntos del minigráfico de 7 días de la tabla (la semana completa de cripto tiene 169). */
    static final int SPARKLINE_POINTS = 28;

    private final TransactionService transactionService;
    private final PortfolioService portfolioService;
    private final AssetService assetService;
    private final PriceProvider priceProvider;
    private final PriceHistoryProvider priceHistoryProvider;
    private final CurrencyConverter converter;

    public HoldingService(TransactionService transactionService, PortfolioService portfolioService,
                          AssetService assetService, PriceProvider priceProvider,
                          PriceHistoryProvider priceHistoryProvider, CurrencyConverter converter) {
        this.transactionService = transactionService;
        this.portfolioService = portfolioService;
        this.assetService = assetService;
        this.priceProvider = priceProvider;
        this.priceHistoryProvider = priceHistoryProvider;
        this.converter = converter;
    }

    /** Tabla de activos: solo lo que se tiene hoy, de mayor a menor capital actual. */
    @Transactional(readOnly = true)
    public HoldingsResponse list(Long userId, Long portfolioId, Currency currency) {
        List<AssetPosition> open = positions(userId, portfolioId, null, currency).stream()
                .filter(AssetPosition::isOpen)
                .toList();
        List<HoldingResponse> holdings = open.stream()
                .map(this::toResponse)
                .sorted(BY_CURRENT_VALUE_DESC)
                .toList();
        return new HoldingsResponse(currency, oldestPriceUpdate(open), holdings);
    }

    /**
     * Hora del precio más viejo entre las posiciones: "precios actualizados hace X" es tan cierto como
     * el precio más atrasado. {@code null} si no hay precios con hora (precios fijos o sin posiciones).
     */
    static Instant oldestPriceUpdate(List<AssetPosition> positions) {
        return positions.stream()
                .map(AssetPosition::priceUpdatedAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    /** Detalle de un activo. Incluye activos ya vendidos por completo (cantidad 0, con su ganancia realizada). */
    @Transactional(readOnly = true)
    public HoldingResponse get(Long userId, String symbol, Long portfolioId, Currency currency) {
        Asset asset = assetService.getBySymbol(symbol);
        return positions(userId, portfolioId, asset.getId(), currency).stream()
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(NO_OPERATIONS_MESSAGE));
    }

    /**
     * Posición de cada activo operado, en la moneda pedida.
     * "Todos los portafolios" es la suma de cada portafolio calculado por separado: así los números
     * de cada portafolio suman exactamente los del total (calcular todo junto daría otro precio promedio).
     */
    List<AssetPosition> positions(Long userId, Long portfolioId, Long assetId, Currency currency) {
        if (portfolioId != null) {
            portfolioService.getOwned(userId, portfolioId); // 404 si no existe o es de otro usuario
        }
        List<Transaction> transactions = transactionService.findForPositions(userId, portfolioId, assetId);

        // activo → portafolio → transacciones (groupingBy conserva el orden cronológico de cada lista)
        Map<Long, Map<Long, List<Transaction>>> byAssetAndPortfolio = transactions.stream()
                .collect(groupingBy(tx -> tx.getAsset().getId(), LinkedHashMap::new,
                        groupingBy(tx -> tx.getPortfolio().getId(), LinkedHashMap::new, toList())));

        return byAssetAndPortfolio.values().stream()
                .map(portfolios -> valuate(portfolios, currency))
                .toList();
    }

    private AssetPosition valuate(Map<Long, List<Transaction>> transactionsByPortfolio, Currency currency) {
        Asset asset = transactionsByPortfolio.values().iterator().next().getFirst().getAsset();
        // Cada operación se convierte con el dólar de su día; la tenencia, con el de hoy.
        Function<LocalDate, BigDecimal> toDisplayCurrency =
                date -> converter.factorOn(date, asset.getCurrency(), currency);

        Position position = transactionsByPortfolio.values().stream()
                .map(portfolioTransactions -> PositionCalculator.calculate(portfolioTransactions, toDisplayCurrency))
                .reduce(Position.EMPTY, Position::plus);
        PriceQuote quote = priceProvider.quote(asset);
        BigDecimal currentPrice = quote.price().multiply(converter.currentFactor(asset.getCurrency(), currency));
        return new AssetPosition(asset, position, currentPrice, quote.updatedAt());
    }

    private HoldingResponse toResponse(AssetPosition position) {
        // La ganancia se calcula con los montos ya redondeados: así capital actual − invertido = pnl, sin diferencias de centavos.
        BigDecimal invested = Rounding.amount(position.investedCapital());
        BigDecimal currentValue = Rounding.amount(position.currentValue());
        BigDecimal pnl = currentValue.subtract(invested);
        Asset asset = position.asset();
        // Solo si ya está cargada: la tabla nunca espera a la fuente del historial (si no, viene null y se pide en segundo plano).
        Optional<PriceHistory> week = priceHistoryProvider.cachedHistory(asset, HistoryRange.WEEK);
        return new HoldingResponse(
                asset.getSymbol(),
                asset.getName(),
                asset.getType(),
                position.quantity(),
                Rounding.unitPrice(position.averagePrice()),
                invested,
                Rounding.unitPrice(position.currentPrice()),
                currentValue,
                pnl,
                Rounding.percent(pnl, invested),
                Rounding.amount(position.realizedPnl()),
                week.map(PriceHistory::changePercent).orElse(null),
                week.map(history -> history.sparkline(SPARKLINE_POINTS)).orElse(null));
    }
}
