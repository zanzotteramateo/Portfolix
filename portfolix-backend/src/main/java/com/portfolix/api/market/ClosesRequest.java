package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;

import java.time.LocalDate;

/**
 * Qué cierres diarios hacen falta de un activo: los de {@code from} a {@code to}, las dos incluidas.
 */
public record ClosesRequest(Asset asset, LocalDate from, LocalDate to) {
}
