package com.portfolix.api.performance;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Análisis contra la app real, con precios fijos (los cierres de cualquier día son el precio fijo).
 * Los números exactos con precios que cambian están en PerformanceServiceTest.
 */
class PerformanceIntegrationTest extends ApiIntegrationTest {

    private RequestPostProcessor juan;
    private long jubilacion;

    @BeforeEach
    void setUp() throws Exception {
        juan = authenticatedAs(createUser("Juan Pérez"));
        jubilacion = createPortfolio(juan, "Jubilación");
        long cripto = createPortfolio(juan, "Cripto");
        // AAPL (precio fijo 12.160 ARS): compra 10 a 11.000 y 10 a 12.000, vende 5 a 13.000.
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "10", "11000", daysAgo(20));
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "10", "12000", daysAgo(10));
        recordTransaction(juan, jubilacion, "AAPL", "SELL", "5", "13000", daysAgo(5));
        recordTransaction(juan, cripto, "BTC", "BUY", "0.5", "100000", daysAgo(15));
        // ETH: compra y venta dentro del período → aparece con cantidad final 0.
        recordTransaction(juan, cripto, "ETH", "BUY", "1", "2000", daysAgo(12));
        recordTransaction(juan, cripto, "ETH", "SELL", "1", "2500", daysAgo(2));
    }

    @Test
    void sinceTheBeginning_isExactlyTheDashboardTotal() throws Exception {
        DocumentContext performance = json(get("/api/v1/performance").with(juan));
        DocumentContext summary = json(get("/api/v1/dashboard/summary").with(juan));

        assertThat(performance.<String>read("$.from")).isEqualTo(daysAgo(20).toString()); // la primera operación
        assertThat(performance.<String>read("$.to")).isEqualTo(LocalDate.now(ARGENTINA).toString());
        assertThat(decimal(performance, "$.startValue")).isZero();
        assertThat(decimal(performance, "$.endValue")).isEqualByComparingTo(decimal(summary, "$.currentValue"));
        assertThat(decimal(performance, "$.pnl")).isEqualByComparingTo(decimal(summary, "$.totalPnl"));
    }

    @Test
    void aPeriod_startsWithWhatWasHeldTheDayBefore() throws Exception {
        DocumentContext performance = json(get("/api/v1/performance").with(juan)
                .param("from", daysAgo(15).toString()));

        // AAPL: tenías 10 (121.600), compraste 120.000, vendiste 65.000 y terminás con 15 (182.400).
        String aapl = "$.assets[?(@.symbol == 'AAPL')]";
        assertThat(decimal(performance, aapl + ".startValue")).isEqualByComparingTo("121600");
        assertThat(decimal(performance, aapl + ".bought")).isEqualByComparingTo("120000");
        assertThat(decimal(performance, aapl + ".sold")).isEqualByComparingTo("65000");
        assertThat(decimal(performance, aapl + ".endValue")).isEqualByComparingTo("182400");
        assertThat(decimal(performance, aapl + ".pnl")).isEqualByComparingTo("5800");
        // ETH se compró y vendió en el período: está, con cantidad final 0.
        assertThat(decimal(performance, "$.assets[?(@.symbol == 'ETH')].endQuantity")).isZero();
        assertThat(performance.<List<String>>read("$.assets[*].symbol")).containsExactlyInAnyOrder("AAPL", "BTC", "ETH");

        // Los totales son la suma de las filas.
        assertThat(decimal(performance, "$.pnl")).isEqualByComparingTo(sum(performance.read("$.assets[*].pnl")));
        assertThat(performance.<String>read("$.best.symbol")).isEqualTo("BTC");
        assertThat((Object) performance.read("$.worst")).isNull();
    }

    @Test
    void series_goesFromTheStartValueToTheEndValue() throws Exception {
        DocumentContext performance = json(get("/api/v1/performance").with(juan)
                .param("from", daysAgo(15).toString()));

        List<String> dates = performance.read("$.series[*].date");
        assertThat(dates).hasSize(17).first().isEqualTo(daysAgo(16).toString()); // el día anterior al inicio
        assertThat(dates.getLast()).isEqualTo(LocalDate.now(ARGENTINA).toString());
        assertThat(decimal(performance, "$.series[0].value")).isEqualByComparingTo(decimal(performance, "$.startValue"));
        assertThat(decimal(performance, "$.series[16].value")).isEqualByComparingTo(decimal(performance, "$.endValue"));
    }

    @Test
    void portfolioAndTypeFilterEverything() throws Exception {
        mockMvc.perform(get("/api/v1/performance").param("portfolioId", String.valueOf(jubilacion)).with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets[*].symbol").value(org.hamcrest.Matchers.contains("AAPL")));
        mockMvc.perform(get("/api/v1/performance").param("type", "CRYPTO").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets[*].symbol").value(org.hamcrest.Matchers.containsInAnyOrder("BTC", "ETH")));
    }

    @Test
    void inDollars() throws Exception {
        DocumentContext performance = json(get("/api/v1/performance").with(juan)
                .param("portfolioId", String.valueOf(jubilacion)).param("currency", "USD"));

        assertThat(performance.<String>read("$.currency")).isEqualTo("USD");
        // 15 AAPL × 12.160 ARS / 1.400 (el dólar fijo)
        assertThat(decimal(performance, "$.endValue")).isEqualByComparingTo("130.29");
    }

    @Test
    void invalidRanges_are400_onTheirField() throws Exception {
        mockMvc.perform(get("/api/v1/performance").with(juan).param("to", daysAgo(-1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("to"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("La fecha de fin no puede ser futura"));
        mockMvc.perform(get("/api/v1/performance").with(juan)
                        .param("from", daysAgo(1).toString()).param("to", daysAgo(3).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"));
        mockMvc.perform(get("/api/v1/performance").with(juan).param("type", "BONDS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void someoneElsesPortfolio_is404() throws Exception {
        RequestPostProcessor ana = authenticatedAs(createUser("Ana García"));

        mockMvc.perform(get("/api/v1/performance").param("portfolioId", String.valueOf(jubilacion)).with(ana))
                .andExpect(status().isNotFound());
    }

    @Test
    void anotherUser_seesNothingOfJuan() throws Exception {
        RequestPostProcessor ana = authenticatedAs(createUser("Ana García"));

        mockMvc.perform(get("/api/v1/performance").with(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assets").isEmpty())
                .andExpect(jsonPath("$.pnl").value("0"));
    }

    @Test
    void withoutSession_is401() throws Exception {
        mockMvc.perform(get("/api/v1/performance")).andExpect(status().isUnauthorized());
    }

    private DocumentContext json(RequestBuilder request) throws Exception {
        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body);
    }

    /** Un número del JSON (vienen como string). Con un filtro ({@code [?(...)]}), el primero que coincide. */
    private static BigDecimal decimal(DocumentContext json, String path) {
        Object value = json.read(path);
        if (value instanceof List<?> list) {
            value = list.getFirst();
        }
        return new BigDecimal(value.toString());
    }

    private static BigDecimal sum(List<String> values) {
        return values.stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
