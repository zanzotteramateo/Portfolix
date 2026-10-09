package com.portfolix.api.portfolio.dto;

import com.portfolix.api.portfolio.Portfolio;

import java.time.Instant;

public record PortfolioResponse(Long id, String name, Instant createdAt) {

    public static PortfolioResponse from(Portfolio portfolio) {
        return new PortfolioResponse(portfolio.getId(), portfolio.getName(), portfolio.getCreatedAt());
    }
}
