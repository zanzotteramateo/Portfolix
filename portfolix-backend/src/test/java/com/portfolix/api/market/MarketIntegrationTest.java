package com.portfolix.api.market;

import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.asset.AssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de mercado contra la app real, con los precios fijos (ver ApiIntegrationTest).
 */
class MarketIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private PriceProvider priceProvider;
    @Autowired
    private FxRateProvider fxRateProvider;
    @Autowired
    private AssetService assetService;

    private RequestPostProcessor juan;

    @BeforeEach
    void setUp() {
        juan = authenticatedAs(createUser("Juan Pérez"));
    }

    @Test
    void quote_inTheAssetCurrencyAndConverted() throws Exception {
        BigDecimal aapl = priceProvider.currentPrice(assetService.getBySymbol("AAPL"));
        BigDecimal rate = fxRateProvider.currentRate();

        mockMvc.perform(get("/api/v1/assets/{symbol}/quote", "aapl").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.price").value(plain(aapl)));
        mockMvc.perform(get("/api/v1/assets/{symbol}/quote", "AAPL").param("currency", "USD").with(juan))
                .andExpect(jsonPath("$.price").value(plain(aapl.divide(rate, MathContext.DECIMAL128)
                        .setScale(8, RoundingMode.HALF_UP))));
    }

    @Test
    void quote_ofTheRenamedDisneyCedear() throws Exception {
        mockMvc.perform(get("/api/v1/assets/{symbol}/quote", "DISN").with(juan))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assets/{symbol}/quote", "DIS").with(juan))
                .andExpect(status().isNotFound());
    }

    @Test
    void fx_showsTheDollarUsedForConversions() throws Exception {
        mockMvc.perform(get("/api/v1/market/fx").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("BLUE"))
                .andExpect(jsonPath("$.rate").value(plain(fxRateProvider.currentRate())));
    }

    @Test
    void history_byRange() throws Exception {
        // Con precios fijos el historial es una línea plana: dos puntos con el mismo precio.
        mockMvc.perform(get("/api/v1/assets/{symbol}/history", "btc").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol").value("BTC"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.range").value("7d"))
                .andExpect(jsonPath("$.changePercent").value("0"))
                .andExpect(jsonPath("$.points.length()").value(2));
        mockMvc.perform(get("/api/v1/assets/{symbol}/history", "GGAL").param("range", "24h").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("24h"))
                .andExpect(jsonPath("$.currency").value("ARS"));
        mockMvc.perform(get("/api/v1/assets/{symbol}/history", "GGAL").param("range", "1m").with(juan))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("range"))
                .andExpect(jsonPath("$.message").value("El rango tiene que ser 24h o 7d"));
    }

    @Test
    void errors() throws Exception {
        mockMvc.perform(get("/api/v1/assets/{symbol}/quote", "NOEXISTE").with(juan))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Activo no encontrado"));
        mockMvc.perform(get("/api/v1/assets/{symbol}/quote", "AAPL").param("currency", "EUR").with(juan))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/market/fx"))
                .andExpect(status().isUnauthorized());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
