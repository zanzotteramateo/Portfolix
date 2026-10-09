package com.portfolix.api.market;

import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.asset.AssetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Si alguien agrega un activo al catálogo (una migración nueva) y se olvida del precio fijo,
 * este test lo avisa antes de que la tabla de activos falle.
 */
class FixedMarketDataTest extends ApiIntegrationTest {

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private PriceProvider priceProvider;
    @Autowired
    private FxRateProvider fxRateProvider;

    @Test
    void everyCatalogAssetHasAPositivePrice() {
        assertThat(assetRepository.findAll())
                .isNotEmpty()
                .allSatisfy(asset -> assertThat(priceProvider.currentPrice(asset))
                        .as("precio de %s", asset.getSymbol())
                        .isPositive());
    }

    @Test
    void theUsdRateIsPositive() {
        assertThat(fxRateProvider.currentRate()).isPositive();
    }
}
