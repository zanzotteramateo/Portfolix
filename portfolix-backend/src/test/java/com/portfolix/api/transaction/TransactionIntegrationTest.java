package com.portfolix.api.transaction;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alta de transacciones y catálogo de activos. El listado se prueba en TransactionListIntegrationTest.
 */
class TransactionIntegrationTest extends ApiIntegrationTest {

    private RequestPostProcessor juan;
    private long portfolioId;

    @BeforeEach
    void setUp() throws Exception {
        juan = authenticatedAs(createUser("Juan Pérez"));
        portfolioId = createPortfolio(juan, "Ahorro Crypto");
    }

    @Test
    void buy_returnsTransactionWithDecimalsAsStrings() throws Exception {
        registerTransaction(juan, portfolioId, "btc", "BUY", "\"0.05\"", "\"72400\"", yesterday(), "\"compra en baja\"")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.portfolio.id").value(portfolioId))
                .andExpect(jsonPath("$.portfolio.name").value("Ahorro Crypto"))
                .andExpect(jsonPath("$.asset.symbol").value("BTC"))
                .andExpect(jsonPath("$.asset.type").value("CRYPTO"))
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.quantity").value("0.05"))
                .andExpect(jsonPath("$.price").value("72400"))
                .andExpect(jsonPath("$.total").value("3620"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.notes").value("compra en baja"));
    }

    @Test
    void acceptsNumbersAsWellAsStrings() throws Exception {
        registerTransaction(juan, portfolioId, "AAPL", "BUY", "35", "11200.5", yesterday(), "null")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantity").value("35"))
                .andExpect(jsonPath("$.price").value("11200.5"))
                .andExpect(jsonPath("$.currency").value("ARS"));
    }

    @Test
    void sell_exceedingHolding_returnsFieldErrorOnQuantity() throws Exception {
        recordTransaction(juan, portfolioId, "BTC", "BUY", "0.3421", "58200", daysAgo(10));

        registerTransaction(juan, portfolioId, "BTC", "SELL", "\"0.5\"", "\"72400\"", yesterday(), "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Supera tu tenencia (0,3421)"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));

        recordTransaction(juan, portfolioId, "BTC", "SELL", "0.3421", "72400", yesterday());
    }

    @Test
    void sell_beforeTheBuyDate_isRejected() throws Exception {
        recordTransaction(juan, portfolioId, "ETH", "BUY", "2", "2180", daysAgo(5));

        registerTransaction(juan, portfolioId, "ETH", "SELL", "\"1\"", "\"2000\"", daysAgo(10), "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Supera tu tenencia a esa fecha (0)"));
    }

    @Test
    void futureDate_isRejectedUsingArgentinaTime() throws Exception {
        LocalDate tomorrowInArgentina = LocalDate.now(ARGENTINA).plusDays(1);

        registerTransaction(juan, portfolioId, "BTC", "BUY", "\"1\"", "\"1\"", tomorrowInArgentina, "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("tradeDate"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("La fecha no puede ser futura"));

        recordTransaction(juan, portfolioId, "BTC", "BUY", "1", "1", LocalDate.now(ARGENTINA));
    }

    @Test
    void invalidFields_areReportedTogether() throws Exception {
        registerTransaction(juan, portfolioId, "BTC", "BUY", "\"0\"", "null", yesterday(), "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(2)))
                .andExpect(jsonPath("$.fieldErrors[*].message", hasItem("La cantidad debe ser mayor a 0")))
                .andExpect(jsonPath("$.fieldErrors[*].message", hasItem("Ingresá un precio")));
    }

    @Test
    void anotherUsersPortfolio_returns404() throws Exception {
        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));

        registerTransaction(ana, portfolioId, "BTC", "BUY", "\"1\"", "\"1\"", yesterday(), "null")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portafolio no encontrado"));
    }

    @Test
    void unknownAsset_returns404() throws Exception {
        registerTransaction(juan, portfolioId, "NOEXISTE", "BUY", "\"1\"", "\"1\"", yesterday(), "null")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Activo no encontrado"));
    }

    @Test
    void concurrentSales_neverSellMoreThanTheHolding() throws Exception {
        recordTransaction(juan, portfolioId, "SOL", "BUY", "3", "112", daysAgo(3));

        // 8 ventas de 1 SOL al mismo tiempo, con una tenencia de 3: solo 3 pueden pasar.
        int attempts = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> sales = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            sales.add(() -> {
                start.await();
                return registerTransaction(juan, portfolioId, "SOL", "SELL", "\"1\"", "\"120\"", yesterday(), "null")
                        .andReturn().getResponse().getStatus();
            });
        }
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(attempts)) {
            List<Future<Integer>> futures = sales.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(3);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(attempts - 3);
    }

    @Test
    void assetsCatalog_canBeFilteredByTypeAndText() throws Exception {
        mockMvc.perform(get("/api/v1/assets").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThan(50))));
        mockMvc.perform(get("/api/v1/assets").param("type", "CRYPTO").param("q", "bit").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].symbol").value("BTC"))
                .andExpect(jsonPath("$[0].currency").value("USD"));
        // Sin distinguir tildes: "energia" encuentra "Pampa Energía".
        mockMvc.perform(get("/api/v1/assets").param("q", "energia").with(juan))
                .andExpect(jsonPath("$[*].symbol", hasItem("PAMP")));
        mockMvc.perform(get("/api/v1/assets").param("type", "BONO").with(juan))
                .andExpect(status().isBadRequest());
    }
}
