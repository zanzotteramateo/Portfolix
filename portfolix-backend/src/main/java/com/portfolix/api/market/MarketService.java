package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.common.Currency;
import com.portfolix.api.common.Rounding;
import com.portfolix.api.market.dto.FxResponse;
import com.portfolix.api.market.dto.HistoryResponse;
import com.portfolix.api.market.dto.QuoteResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class MarketService {

    private final AssetService assetService;
    private final PriceProvider priceProvider;
    private final FxRateProvider fxRateProvider;
    private final PriceHistoryProvider priceHistoryProvider;
    private final CurrencyConverter converter;

    public MarketService(AssetService assetService, PriceProvider priceProvider, FxRateProvider fxRateProvider,
                         PriceHistoryProvider priceHistoryProvider, CurrencyConverter converter) {
        this.assetService = assetService;
        this.priceProvider = priceProvider;
        this.fxRateProvider = fxRateProvider;
        this.priceHistoryProvider = priceHistoryProvider;
        this.converter = converter;
    }

    public QuoteResponse quote(String symbol, Currency currency) {
        Asset asset = assetService.getBySymbol(symbol);
        PriceQuote quote = priceProvider.quote(asset);
        BigDecimal price = quote.price().multiply(converter.currentFactor(asset.getCurrency(), currency));
        BigDecimal change = quote.change24hPercent() == null
                ? null
                : quote.change24hPercent().setScale(2, RoundingMode.HALF_UP);
        return new QuoteResponse(asset.getSymbol(), currency, Rounding.unitPrice(price), change, quote.updatedAt());
    }

    /** Gráfico de tendencia del panel de detalle: espera a la fuente si todavía no está cargado. */
    public HistoryResponse history(String symbol, String rangeParam) {
        HistoryRange range = HistoryRange.fromParam(rangeParam);
        PriceHistory history = priceHistoryProvider.history(assetService.getBySymbol(symbol), range);
        List<HistoryResponse.Point> points = history.points().stream()
                .map(point -> new HistoryResponse.Point(point.time(), Rounding.unitPrice(point.price())))
                .toList();
        return new HistoryResponse(history.symbol(), history.currency(), range.param(), history.changePercent(), points);
    }

    public FxResponse fx() {
        FxQuote quote = fxRateProvider.currentQuote();
        return new FxResponse(quote.type(), quote.buy(), quote.sell(), quote.rate(), quote.updatedAt());
    }
}
