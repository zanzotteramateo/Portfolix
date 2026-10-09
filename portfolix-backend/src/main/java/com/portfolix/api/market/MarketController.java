package com.portfolix.api.market;

import com.portfolix.api.common.Currency;
import com.portfolix.api.market.dto.FxResponse;
import com.portfolix.api.market.dto.HistoryResponse;
import com.portfolix.api.market.dto.QuoteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * La cotización vive bajo /assets, pero el endpoint está en el módulo market: así asset no depende
 * de market (market ya depende de asset para saber qué activos pedir).
 */
@RestController
@RequestMapping("/api/v1")
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    /** Precio actual de un activo en la moneda pedida (ARS por defecto). */
    @GetMapping("/assets/{symbol}/quote")
    public QuoteResponse quote(@PathVariable String symbol,
                               @RequestParam(defaultValue = "ARS") Currency currency) {
        return marketService.quote(symbol, currency);
    }

    /**
     * Gráfico de tendencia: {@code range=7d} (default) o {@code 24h}. Para acciones y CEDEARs el de 24 h
     * tiene dos puntos (cierre anterior → precio actual): las fuentes no tienen datos dentro del día.
     */
    @GetMapping("/assets/{symbol}/history")
    public HistoryResponse history(@PathVariable String symbol,
                                   @RequestParam(defaultValue = "7d") String range) {
        return marketService.history(symbol, range);
    }

    /** Dólar que usa la app para convertir entre ARS y USD. */
    @GetMapping("/market/fx")
    public FxResponse fx() {
        return marketService.fx();
    }
}
