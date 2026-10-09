package com.portfolix.api.holding;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.market.FxRateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Dashboard contra la app real. Lo central: la cabecera cierra con la tabla de activos.
 */
class DashboardIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private FxRateProvider fxRateProvider;

    private RequestPostProcessor juan;
    private long jubilacion;

    @BeforeEach
    void setUp() throws Exception {
        juan = authenticatedAs(createUser("Juan Pérez"));
        jubilacion = createPortfolio(juan, "Jubilación");
        long cripto = createPortfolio(juan, "Cripto");
        // AAPL: compra 10 a 11.000 y 10 a 12.000, vende 5 a 13.000 → ganancia realizada 7.500 ARS.
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "10", "11000", daysAgo(20));
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "10", "12000", daysAgo(10));
        recordTransaction(juan, jubilacion, "AAPL", "SELL", "5", "13000", daysAgo(5));
        recordTransaction(juan, cripto, "BTC", "BUY", "0.5", "100000", daysAgo(15));
        // ETH: compra 1 a US$2.000 y lo vende a US$2.500 → ganancia realizada US$500, ya no se tiene.
        recordTransaction(juan, cripto, "ETH", "BUY", "1", "2000", daysAgo(12));
        recordTransaction(juan, cripto, "ETH", "SELL", "1", "2500", daysAgo(2));
    }

    @Test
    void summary_closesWithTheHoldingsTable() throws Exception {
        DocumentContext holdings = json(get("/api/v1/holdings").with(juan));
        DocumentContext summary = json(get("/api/v1/dashboard/summary").with(juan));
        BigDecimal rate = fxRateProvider.currentRate();

        BigDecimal tableValue = sum(holdings.read("$.holdings[*].currentValue"));
        BigDecimal tableInvested = sum(holdings.read("$.holdings[*].investedCapital"));
        BigDecimal currentValue = decimal(summary, "$.currentValue");
        BigDecimal invested = decimal(summary, "$.investedCapital");
        BigDecimal realized = decimal(summary, "$.realizedPnl");
        BigDecimal totalPnl = decimal(summary, "$.totalPnl");

        assertThat(currentValue).isEqualByComparingTo(tableValue);
        assertThat(invested).isEqualByComparingTo(tableInvested);
        assertThat(realized).isEqualByComparingTo(new BigDecimal("7500").add(new BigDecimal("500").multiply(rate)));
        assertThat(totalPnl).isEqualByComparingTo(currentValue.subtract(invested).add(realized));
        assertThat((Integer) summary.read("$.holdingsCount")).isEqualTo(2);

        // El porcentaje es sobre todo lo comprado: 230.000 ARS de AAPL + (50.000 + 2.000) USD de cripto.
        BigDecimal bought = new BigDecimal("230000").add(new BigDecimal("52000").multiply(rate));
        assertThat(decimal(summary, "$.totalPnlPercent"))
                .isEqualByComparingTo(totalPnl.multiply(BigDecimal.valueOf(100)).divide(bought, 2, RoundingMode.HALF_UP));

        assertThat(sum(summary.read("$.distribution[*].percent"))).isEqualByComparingTo("100");
        assertThat((String) summary.read("$.fx.type")).isEqualTo("BLUE");
        assertThat(decimal(summary, "$.fx.rate")).isEqualByComparingTo(rate);
    }

    @Test
    void summary_byPortfolio() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").param("portfolioId", String.valueOf(jubilacion)).with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdingsCount").value(1))
                .andExpect(jsonPath("$.investedCapital").value("172500"))
                .andExpect(jsonPath("$.realizedPnl").value("7500"))
                .andExpect(jsonPath("$.distribution[?(@.type == 'CEDEAR')].percent").value("100"));
    }

    @Test
    void emptyPortfolio_returnsZerosAndNullPercentages() throws Exception {
        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));

        mockMvc.perform(get("/api/v1/dashboard/summary").with(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentValue").value("0"))
                .andExpect(jsonPath("$.totalPnl").value("0"))
                .andExpect(jsonPath("$.totalPnlPercent").value(nullValue()))
                .andExpect(jsonPath("$.holdingsCount").value(0))
                .andExpect(jsonPath("$.distribution[*].percent").value(everyItem(nullValue())));
    }

    @Test
    void anotherUsersPortfolio_returns404() throws Exception {
        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));

        mockMvc.perform(get("/api/v1/dashboard/summary").param("portfolioId", String.valueOf(jubilacion)).with(ana))
                .andExpect(status().isNotFound());
    }

    private DocumentContext json(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body);
    }

    private static BigDecimal decimal(DocumentContext json, String path) {
        return new BigDecimal(json.<String>read(path));
    }

    private static BigDecimal sum(List<String> values) {
        return values.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
