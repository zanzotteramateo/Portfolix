package com.portfolix.api.transaction;

import java.time.LocalDate;

/**
 * Filtros opcionales del historial de transacciones. {@code null} = sin filtrar por ese campo.
 * Las fechas incluyen los extremos.
 */
public record TransactionFilter(
        Long portfolioId,
        String assetSymbol,
        TransactionType type,
        LocalDate from,
        LocalDate to
) {
}
