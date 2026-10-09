package com.portfolix.api.transaction;

import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.market.FxRateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Historial de transacciones: filtros, orden, paginación y resumen.
 */
class TransactionListIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private FxRateProvider fxRateProvider;

    private RequestPostProcessor juan;
    private long jubilacion;
    private long anasPortfolio;

    /**
     * Historial de Juan, del más nuevo al más viejo:
     * <pre>
     * #5  hace 3 días   Ahorro Crypto  SELL BTC  0.05 × 68200 =   3410 USD
     * #4  hace 5 días   Jubilación     SELL AAPL   10 × 12000 = 120000 ARS
     * #3  hace 10 días  Ahorro Crypto  BUY  BTC   0.1 × 72400 =   7240 USD
     * #2  hace 20 días  Jubilación     BUY  MELI    3 × 62000 = 186000 ARS  (mismo día que #1, cargada después)
     * #1  hace 20 días  Jubilación     BUY  AAPL   35 × 11200 = 392000 ARS
     * </pre>
     * Ana tiene una compra de ETH que nunca tiene que aparecer en el historial de Juan.
     */
    @BeforeEach
    void setUp() throws Exception {
        juan = authenticatedAs(createUser("Juan Pérez"));
        jubilacion = createPortfolio(juan, "Jubilación");
        long ahorroCrypto = createPortfolio(juan, "Ahorro Crypto");
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "35", "11200", daysAgo(20));
        recordTransaction(juan, jubilacion, "MELI", "BUY", "3", "62000", daysAgo(20));
        recordTransaction(juan, ahorroCrypto, "BTC", "BUY", "0.1", "72400", daysAgo(10));
        recordTransaction(juan, jubilacion, "AAPL", "SELL", "10", "12000", daysAgo(5));
        recordTransaction(juan, ahorroCrypto, "BTC", "SELL", "0.05", "68200", daysAgo(3));

        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));
        anasPortfolio = createPortfolio(ana, "Cripto de Ana");
        recordTransaction(ana, anasPortfolio, "ETH", "BUY", "1", "2000", daysAgo(7));
    }

    @Test
    void withoutFilters_listsOwnTransactionsNewestFirst() throws Exception {
        list()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].asset.symbol").value(contains("BTC", "AAPL", "BTC", "MELI", "AAPL")))
                .andExpect(jsonPath("$.content[0].type").value("SELL"))
                .andExpect(jsonPath("$.content[0].quantity").value("0.05"))
                .andExpect(jsonPath("$.content[0].total").value("3410"))
                .andExpect(jsonPath("$.content[0].currency").value("USD"))
                .andExpect(jsonPath("$.content[0].asset.name").value("Bitcoin"))
                .andExpect(jsonPath("$.content[0].portfolio.name").value("Ahorro Crypto"))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(50))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(1));
    }

    @Test
    void summary_separatesCurrencies_andIgnoresTypeFilter() throws Exception {
        list("type", "SELL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].asset.symbol").value(contains("BTC", "AAPL")))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                // Las tarjetas del resumen no cambian al tocar "Ventas".
                .andExpect(jsonPath("$.summary.totalOperations").value(5))
                .andExpect(jsonPath("$.summary.totalBought.ARS").value("578000"))
                .andExpect(jsonPath("$.summary.totalBought.USD").value("7240"))
                .andExpect(jsonPath("$.summary.totalSold.ARS").value("120000"))
                .andExpect(jsonPath("$.summary.totalSold.USD").value("3410"));
    }

    @Test
    void summary_includesTotalsConvertedToOneCurrency() throws Exception {
        // El dólar fijo es el mismo para todas las fechas; con histórico real, cada fila usa el de su día.
        BigDecimal rate = fxRateProvider.currentRate();

        list("currency", "ARS")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.converted.currency").value("ARS"))
                .andExpect(jsonPath("$.summary.converted.totalBought")
                        .value(amount(new BigDecimal("578000").add(new BigDecimal("7240").multiply(rate)))))
                .andExpect(jsonPath("$.summary.converted.totalSold")
                        .value(amount(new BigDecimal("120000").add(new BigDecimal("3410").multiply(rate)))));

        list("currency", "USD")
                .andExpect(jsonPath("$.summary.converted.currency").value("USD"))
                .andExpect(jsonPath("$.summary.converted.totalBought")
                        .value(amount(new BigDecimal("578000").divide(rate, MathContext.DECIMAL128).add(new BigDecimal("7240")))))
                // Los totales por moneda no cambian: siguen separados y exactos.
                .andExpect(jsonPath("$.summary.totalBought.ARS").value("578000"));
    }

    @Test
    void filtersCombine_andTheSummaryRespectsThem() throws Exception {
        list("portfolioId", String.valueOf(jubilacion), "assetSymbol", "aapl")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].type").value(contains("SELL", "BUY")))
                .andExpect(jsonPath("$.summary.totalOperations").value(2))
                .andExpect(jsonPath("$.summary.totalBought.ARS").value("392000"))
                .andExpect(jsonPath("$.summary.totalBought.USD").value("0"))
                .andExpect(jsonPath("$.summary.totalSold.ARS").value("120000"));

        // Rango de fechas con los extremos incluidos.
        list("from", daysAgo(10).toString(), "to", daysAgo(5).toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].asset.symbol").value(contains("AAPL", "BTC")))
                .andExpect(jsonPath("$.summary.totalOperations").value(2));
    }

    @Test
    void pagination_theSummaryCoversAllPages() throws Exception {
        list("size", "2", "page", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].asset.symbol").value(contains("BTC", "MELI")))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(5))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.summary.totalOperations").value(5));
    }

    @Test
    void filterByAnotherUsersPortfolioOrUnknownAsset_returns404() throws Exception {
        list("portfolioId", String.valueOf(anasPortfolio))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portafolio no encontrado"));
        list("assetSymbol", "NOEXISTE")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Activo no encontrado"));
    }

    @Test
    void invalidParameters_return400() throws Exception {
        list("size", "101")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("size"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("El tamaño de página puede ser hasta 100"));
        list("from", daysAgo(1).toString(), "to", daysAgo(5).toString())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("from"))
                .andExpect(jsonPath("$.message").value("La fecha desde no puede ser posterior a la fecha hasta"));
        list("type", "COMPRA")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'type' tiene un formato inválido"));
        list("from", "25/09/2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'from' tiene un formato inválido"));
    }

    /** Mismo formato que la API: 2 decimales y sin ceros de relleno. */
    private static String amount(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    /** GET /transactions como Juan, con parámetros en pares nombre-valor. */
    private ResultActions list(String... params) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/transactions").with(juan);
        for (int i = 0; i < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
        }
        return mockMvc.perform(request);
    }
}
