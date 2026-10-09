package com.portfolix.api.transaction;

import com.portfolix.api.common.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila del resumen agregado: cantidad de operaciones y monto total (cantidad × precio)
 * para una combinación de moneda, tipo y día. Ej.: (ARS, BUY, 2026-09-25, 3, 488434).
 * Se agrupa también por día para poder convertir cada monto con el dólar de esa fecha.
 */
public record TransactionTotals(Currency currency, TransactionType type, LocalDate tradeDate, Long count,
                                BigDecimal amount) {
}
