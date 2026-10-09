package com.portfolix.api.transaction;

import com.portfolix.api.common.Currency;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Arma con la Criteria API el equivalente a:
 * <pre>
 * select currency, type, trade_date, count(*), sum(quantity * price)
 * from transactions ... where (condición) group by currency, type, trade_date
 * </pre>
 * La condición es la misma Specification que usa el listado, así los totales respetan los filtros.
 */
class TransactionSummaryRepositoryImpl implements TransactionSummaryRepository {

    private final EntityManager entityManager;

    TransactionSummaryRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<TransactionTotals> summarize(Specification<Transaction> condition) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<TransactionTotals> query = cb.createQuery(TransactionTotals.class);
        Root<Transaction> root = query.from(Transaction.class);

        Path<Currency> currency = root.get("currency");
        Path<TransactionType> type = root.get("type");
        Path<LocalDate> tradeDate = root.get("tradeDate");
        Expression<BigDecimal> amount = cb.prod(root.<BigDecimal>get("quantity"), root.<BigDecimal>get("price"));

        query.select(cb.construct(TransactionTotals.class, currency, type, tradeDate, cb.count(root), cb.sum(amount)))
                .where(condition.toPredicate(root, query, cb))
                .groupBy(currency, type, tradeDate);
        return entityManager.createQuery(query).getResultList();
    }
}
