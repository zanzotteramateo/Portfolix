package com.portfolix.api.transaction;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * Piezas de filtro para consultar transacciones. Cada método es una condición;
 * el service combina solo las que corresponden a los filtros que llegaron en el request.
 */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<Transaction> belongsToUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("portfolio").get("user").get("id"), userId);
    }

    static Specification<Transaction> inPortfolio(Long portfolioId) {
        return (root, query, cb) -> cb.equal(root.get("portfolio").get("id"), portfolioId);
    }

    static Specification<Transaction> forAsset(Long assetId) {
        return (root, query, cb) -> cb.equal(root.get("asset").get("id"), assetId);
    }

    static Specification<Transaction> ofType(TransactionType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    static Specification<Transaction> tradedOnOrAfter(LocalDate from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.<LocalDate>get("tradeDate"), from);
    }

    static Specification<Transaction> tradedOnOrBefore(LocalDate to) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.<LocalDate>get("tradeDate"), to);
    }
}
