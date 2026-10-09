package com.portfolix.api.holding;

import com.portfolix.api.common.Currency;
import com.portfolix.api.holding.dto.DashboardSummaryResponse;
import com.portfolix.api.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Sin {@code portfolioId}: todos los portafolios. */
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@CurrentUserId Long userId,
                                            @RequestParam(required = false) Long portfolioId,
                                            @RequestParam(defaultValue = "ARS") Currency currency) {
        return dashboardService.summary(userId, portfolioId, currency);
    }
}
