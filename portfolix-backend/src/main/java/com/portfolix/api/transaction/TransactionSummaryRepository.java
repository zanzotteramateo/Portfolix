package com.portfolix.api.transaction;

import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Consulta propia que Spring Data no puede generar sola (agregación con filtros dinámicos).
 * TransactionRepository la hereda y Spring usa la implementación {@code TransactionSummaryRepositoryImpl}.
 */
public interface TransactionSummaryRepository {

    /** Cantidad y monto de las transacciones que cumplen la condición, agrupados por moneda y tipo. */
    List<TransactionTotals> summarize(Specification<Transaction> condition);
}
