package com.portfolix.api.transaction.dto;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.common.Currency;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * @param total cantidad × precio, calculado (no se guarda)
 */
public record TransactionResponse(
        Long id,
        PortfolioRef portfolio,
        AssetRef asset,
        TransactionType type,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal total,
        Currency currency,
        LocalDate tradeDate,
        String notes,
        Instant createdAt
) {

    public record PortfolioRef(Long id, String name) {
        static PortfolioRef from(Portfolio portfolio) {
            return new PortfolioRef(portfolio.getId(), portfolio.getName());
        }
    }

    public record AssetRef(String symbol, String name, AssetType type) {
        static AssetRef from(Asset asset) {
            return new AssetRef(asset.getSymbol(), asset.getName(), asset.getType());
        }
    }

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                PortfolioRef.from(tx.getPortfolio()),
                AssetRef.from(tx.getAsset()),
                tx.getType(),
                tx.getQuantity(),
                tx.getPrice(),
                tx.getQuantity().multiply(tx.getPrice()),
                tx.getCurrency(),
                tx.getTradeDate(),
                tx.getNotes(),
                tx.getCreatedAt());
    }
}
