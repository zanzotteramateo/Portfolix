package com.portfolix.api.holding;

import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.asset.AssetService;
import com.portfolix.api.market.FxRateProvider;
import com.portfolix.api.market.PriceProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Holdings contra la app real. Los precios salen de market/fixed-prices.yml, que se puede editar:
 * por eso los valores actuales esperados se calculan con el mismo PriceProvider, y lo que se verifica
 * con números fijos es lo que no depende del precio (cantidad, promedio, invertido, ganancia realizada).
 * La aritmética exacta con precios controlados se prueba en HoldingServiceTest.
 */
class HoldingIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private PriceProvider priceProvider;
    @Autowired
    private FxRateProvider fxRateProvider;
    @Autowired
    private AssetService assetService;

    private RequestPostProcessor juan;
    private long jubilacion;
    private long cripto;

    @BeforeEach
    void setUp() throws Exception {
        juan = authenticatedAs(createUser("Juan Pérez"));
        jubilacion = createPortfolio(juan, "Jubilación");
        cripto = createPortfolio(juan, "Cripto");
        // AAPL: compra 10 a 11.000 y 10 a 12.000 (promedio 11.500), vende 5 a 13.000 → ganancia 7.500; quedan 15.
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "10", "11000", daysAgo(20));
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "10", "12000", daysAgo(10));
        recordTransaction(juan, jubilacion, "AAPL", "SELL", "5", "13000", daysAgo(5));
        // BTC: compra 0,5 a US$100.000.
        recordTransaction(juan, cripto, "BTC", "BUY", "0.5", "100000", daysAgo(15));
        // ETH: compra 1 a US$2.000 y lo vende todo a US$2.500 → ganancia 500, ya no se tiene.
        recordTransaction(juan, cripto, "ETH", "BUY", "1", "2000", daysAgo(12));
        recordTransaction(juan, cripto, "ETH", "SELL", "1", "2500", daysAgo(2));

        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));
        recordTransaction(ana, createPortfolio(ana, "De Ana"), "AAPL", "BUY", "3", "12000", daysAgo(1));
    }

    @Test
    void list_showsOpenPositionsOfTheUserOnly() throws Exception {
        holdings("currency", "ARS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.holdings[*].symbol").value(containsInAnyOrder("AAPL", "BTC")))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].quantity").value("15"))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].averagePrice").value("11500"))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].investedCapital").value("172500"))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].realizedPnl").value("7500"))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].currentValue")
                        .value(amount(price("AAPL").multiply(new BigDecimal("15")))))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'BTC')].investedCapital")
                        .value(amount(new BigDecimal("50000").multiply(fxRateProvider.currentRate()))))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'BTC')].currentValue")
                        .value(amount(price("BTC").multiply(new BigDecimal("0.5")).multiply(fxRateProvider.currentRate()))));
    }

    @Test
    void list_includesTheSevenDayTrend() throws Exception {
        // Con precios fijos la tendencia es plana.
        holdings("currency", "ARS")
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].change7dPercent").value("0"))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].sparkline7d.length()").value(2));
    }

    @Test
    void list_inUsd_convertsTheCedears() throws Exception {
        BigDecimal rate = fxRateProvider.currentRate(); // el dólar fijo es el mismo para todas las fechas

        holdings("currency", "USD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'BTC')].investedCapital").value("50000"))
                .andExpect(jsonPath("$.holdings[?(@.symbol == 'AAPL')].investedCapital")
                        .value(amount(new BigDecimal("172500").divide(rate, MathContext.DECIMAL128))));
    }

    @Test
    void list_byPortfolio() throws Exception {
        holdings("portfolioId", String.valueOf(cripto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdings[*].symbol").value(containsInAnyOrder("BTC")));
    }

    @Test
    void detail_ofASoldOutAsset_showsItsRealizedPnl() throws Exception {
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "eth").param("currency", "USD").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("ETH"))
                .andExpect(jsonPath("$.quantity").value("0"))
                .andExpect(jsonPath("$.averagePrice").value(nullValue()))
                .andExpect(jsonPath("$.pnlPercent").value(nullValue()))
                .andExpect(jsonPath("$.realizedPnl").value("500"));
    }

    @Test
    void notFoundCases() throws Exception {
        RequestPostProcessor pedro = authenticatedAs(createUser("Pedro"));
        long foreignPortfolio = createPortfolio(pedro, "De Pedro");

        holdings("portfolioId", String.valueOf(foreignPortfolio))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portafolio no encontrado"));
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "MSFT").with(juan))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No tenés operaciones de este activo"));
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "NOEXISTE").with(juan))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Activo no encontrado"));
    }

    @Test
    void invalidCurrency_returns400() throws Exception {
        holdings("currency", "EUR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'currency' tiene un formato inválido"));
    }

    private ResultActions holdings(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/holdings").with(juan);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private BigDecimal price(String symbol) {
        return priceProvider.currentPrice(assetService.getBySymbol(symbol));
    }

    /** Mismo formato que la API: 2 decimales y sin ceros de relleno. */
    private static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
