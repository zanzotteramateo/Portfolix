package com.portfolix.api.market;

import com.portfolix.api.asset.Asset;

import java.math.BigDecimal;

/**
 * De dónde salen los precios. El resto de la app depende de esta interfaz, no de una fuente concreta:
 * LiveMarketData (APIs reales) o FixedMarketData (valores fijos), según {@code portfolix.market.provider}.
 */
public interface PriceProvider {

    /**
     * Cotización actual de un activo, en su moneda (ARS para acciones y CEDEARs, USD para cripto).
     *
     * @throws com.portfolix.api.common.exception.ServiceUnavailableException si no hay ningún precio disponible
     */
    PriceQuote quote(Asset asset);

    default BigDecimal currentPrice(Asset asset) {
        return quote(asset).price();
    }
}
