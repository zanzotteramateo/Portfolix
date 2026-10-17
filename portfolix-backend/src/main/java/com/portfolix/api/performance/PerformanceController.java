package com.portfolix.api.performance;

import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.performance.dto.PerformanceResponse;
import com.portfolix.api.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Análisis de un período. Sin {@code from}: desde la primera operación; sin {@code to}: hasta hoy.
 * Sin {@code portfolioId} ni {@code type}: todos los portafolios y todos los tipos de activo.
 */
@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    @GetMapping
    public PerformanceResponse performance(@CurrentUserId Long userId,
                                           @RequestParam(required = false) Long portfolioId,
                                           @RequestParam(required = false) AssetType type,
                                           @RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to,
                                           @RequestParam(defaultValue = "ARS") Currency currency) {
        return performanceService.performance(userId, portfolioId, type, from, to, currency);
    }
}
