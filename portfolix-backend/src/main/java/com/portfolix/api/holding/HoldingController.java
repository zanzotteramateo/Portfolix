package com.portfolix.api.holding;

import com.portfolix.api.common.Currency;
import com.portfolix.api.holding.dto.HoldingResponse;
import com.portfolix.api.holding.dto.HoldingsResponse;
import com.portfolix.api.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sin {@code portfolioId} se muestran todos los portafolios juntos.
 * {@code currency} es la moneda de todos los montos (ARS por defecto hasta que existan las preferencias).
 */
@RestController
@RequestMapping("/api/v1/holdings")
public class HoldingController {

    private final HoldingService holdingService;

    public HoldingController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    @GetMapping
    public HoldingsResponse list(@CurrentUserId Long userId,
                                 @RequestParam(required = false) Long portfolioId,
                                 @RequestParam(defaultValue = "ARS") Currency currency) {
        return holdingService.list(userId, portfolioId, currency);
    }

    @GetMapping("/{symbol}")
    public HoldingResponse get(@CurrentUserId Long userId,
                               @PathVariable String symbol,
                               @RequestParam(required = false) Long portfolioId,
                               @RequestParam(defaultValue = "ARS") Currency currency) {
        return holdingService.get(userId, symbol, portfolioId, currency);
    }
}
