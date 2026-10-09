package com.portfolix.api.holding.dto;

import com.portfolix.api.common.Currency;

import java.time.Instant;
import java.util.List;

/**
 * Tabla de activos: posiciones abiertas, de mayor a menor capital actual.
 *
 * @param currency        moneda en la que están todos los montos
 * @param pricesUpdatedAt hora del precio más viejo usado ("precios actualizados hace X");
 *                        {@code null} con precios fijos o sin posiciones
 */
public record HoldingsResponse(Currency currency, Instant pricesUpdatedAt, List<HoldingResponse> holdings) {
}
