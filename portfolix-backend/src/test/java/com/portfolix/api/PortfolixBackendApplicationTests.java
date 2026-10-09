package com.portfolix.api;

import com.portfolix.api.market.FxRateProvider;
import com.portfolix.api.market.PriceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La app arranca con la configuración real (datos de mercado en vivo). Las APIs apuntan a una
 * dirección donde no hay nada: la precarga falla rápido y sin salir a internet, pero se verifica
 * que todas las piezas del proveedor real se conectan.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"portfolix.market.provider=live",
		"portfolix.market.sources.dolar-api=http://localhost:1",
		"portfolix.market.sources.argentina-datos=http://localhost:1",
		"portfolix.market.sources.binance=http://localhost:1",
		"portfolix.market.sources.data912=http://localhost:1"
})
class PortfolixBackendApplicationTests {

	@Autowired
	private PriceProvider priceProvider;
	@Autowired
	private FxRateProvider fxRateProvider;

	@Test
	void contextLoads_withLiveMarketData() {
		assertThat(priceProvider.getClass().getSimpleName()).isEqualTo("LiveMarketData");
		assertThat(fxRateProvider).isSameAs(priceProvider);
	}

}
