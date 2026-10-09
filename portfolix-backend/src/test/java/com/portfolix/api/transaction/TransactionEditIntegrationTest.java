package com.portfolix.api.transaction;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Consulta, edición y borrado de una transacción. La regla central: ningún cambio puede dejar
 * a una venta sin tenencia suficiente.
 */
class TransactionEditIntegrationTest extends ApiIntegrationTest {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private RequestPostProcessor juan;
    private long jubilacion;

    @BeforeEach
    void setUp() throws Exception {
        juan = authenticatedAs(createUser("Juan Pérez"));
        jubilacion = createPortfolio(juan, "Jubilación");
    }

    // ---- GET /transactions/{id} ----

    @Test
    void get_returnsTheTransaction() throws Exception {
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500.5", daysAgo(3));

        getTransaction(id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.portfolio.name").value("Jubilación"))
                .andExpect(jsonPath("$.asset.symbol").value("YPFD"))
                .andExpect(jsonPath("$.type").value("BUY"))
                .andExpect(jsonPath("$.quantity").value("10"))
                .andExpect(jsonPath("$.total").value("415005"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.tradeDate").value(daysAgo(3).toString()));
    }

    @Test
    void anotherUsersTransaction_isNotFound() throws Exception {
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(3));
        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));

        mockMvc.perform(get("/api/v1/transactions/{id}", id).with(ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transacción no encontrada"));
        mockMvc.perform(delete("/api/v1/transactions/{id}", id).with(ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transacción no encontrada"));

        getTransaction(id).andExpect(status().isOk()); // la de Juan sigue ahí
    }

    @Test
    void missingTransaction_isNotFound() throws Exception {
        getTransaction(Long.MAX_VALUE).andExpect(status().isNotFound());
        deleteTransaction(Long.MAX_VALUE).andExpect(status().isNotFound());
    }

    // ---- DELETE /transactions/{id} ----

    @Test
    void delete_removesTheTransactionAndTheHoldingFollows() throws Exception {
        recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5));
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "4", "42000", daysAgo(3));

        deleteTransaction(id).andExpect(status().isNoContent());

        getTransaction(id).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "YPFD").with(juan))
                .andExpect(jsonPath("$.quantity").value("10"))
                .andExpect(jsonPath("$.averagePrice").value("41500"));
    }

    @Test
    void delete_ofBuyThatCoversALaterSale_isRejectedNamingTheSale() throws Exception {
        recordTransaction(juan, jubilacion, "YPFD", "BUY", "5", "41500", daysAgo(10));
        long secondBuy = recordTransaction(juan, jubilacion, "YPFD", "BUY", "5", "42000", daysAgo(8));
        recordTransaction(juan, jubilacion, "YPFD", "SELL", "8", "45000", daysAgo(3));

        String expected = "No se puede eliminar: la venta de 8 YPFD del %s en Jubilación "
                .formatted(DAY.format(daysAgo(3))) + "quedaría sin tenencia suficiente";
        deleteTransaction(secondBuy)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(expected))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        getTransaction(secondBuy).andExpect(status().isOk());
    }

    @Test
    void delete_ofSale_isAlwaysAllowed() throws Exception {
        recordTransaction(juan, jubilacion, "BTC", "BUY", "1", "60000", daysAgo(10));
        long firstSale = recordTransaction(juan, jubilacion, "BTC", "SELL", "0.4", "65000", daysAgo(5));
        recordTransaction(juan, jubilacion, "BTC", "SELL", "0.6", "70000", daysAgo(2));

        deleteTransaction(firstSale).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/holdings/{symbol}", "BTC").with(juan))
                .andExpect(jsonPath("$.quantity").value("0.4"));
    }

    @Test
    void delete_onlyChecksTheSalesOfThatPortfolio() throws Exception {
        long trading = createPortfolio(juan, "Trading");
        recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(10));
        recordTransaction(juan, jubilacion, "YPFD", "SELL", "10", "45000", daysAgo(3));
        long buyInTrading = recordTransaction(juan, trading, "YPFD", "BUY", "10", "41500", daysAgo(10));

        deleteTransaction(buyInTrading).andExpect(status().isNoContent());
    }

    @Test
    void concurrentDeletes_neverLeaveASaleUncovered() throws Exception {
        List<Long> buys = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            buys.add(recordTransaction(juan, jubilacion, "SOL", "BUY", "5", "110", daysAgo(10)));
        }
        recordTransaction(juan, jubilacion, "SOL", "SELL", "8", "120", daysAgo(2));

        // Borrar cualquiera de las compras deja 10 (alcanza para la venta de 8), pero borrar dos deja 5.
        // Si se validaran al mismo tiempo, las tres verían 15 y pasarían: con el bloqueo, solo pasa una.
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Integer>> deletions = buys.stream().<Callable<Integer>>map(id -> () -> {
            start.await();
            return deleteTransaction(id).andReturn().getResponse().getStatus();
        }).toList();
        List<Integer> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(deletions.size())) {
            List<Future<Integer>> futures = deletions.stream().map(executor::submit).toList();
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get());
            }
        }

        assertThat(statuses).filteredOn(s -> s == 204).hasSize(1);
        assertThat(statuses).filteredOn(s -> s == 400).hasSize(2);
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "SOL").with(juan))
                .andExpect(jsonPath("$.quantity").value("2"));
    }

    // ---- PUT /transactions/{id} ----

    @Test
    void update_replacesAllFieldsAndTheHoldingFollows() throws Exception {
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5));

        updateTransaction(id, jubilacion, "ypfd", "BUY", "12", "40000", daysAgo(4), "  corregida ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.quantity").value("12"))
                .andExpect(jsonPath("$.price").value("40000"))
                .andExpect(jsonPath("$.total").value("480000"))
                .andExpect(jsonPath("$.tradeDate").value(daysAgo(4).toString()))
                .andExpect(jsonPath("$.notes").value("corregida"));

        getTransaction(id).andExpect(jsonPath("$.quantity").value("12"));
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "YPFD").with(juan))
                .andExpect(jsonPath("$.quantity").value("12"))
                .andExpect(jsonPath("$.averagePrice").value("40000"));
    }

    @Test
    void update_withoutNotes_clearsThem() throws Exception {
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5));
        updateTransaction(id, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5), "con nota")
                .andExpect(status().isOk());

        updateTransaction(id, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").doesNotExist());
    }

    @Test
    void update_changingTheAsset_copiesItsCurrency() throws Exception {
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5));

        updateTransaction(id, jubilacion, "BTC", "BUY", "0.01", "60000", daysAgo(5), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asset.symbol").value("BTC"))
                .andExpect(jsonPath("$.currency").value("USD"));

        getTransaction(id).andExpect(jsonPath("$.currency").value("USD"));
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "YPFD").with(juan)).andExpect(status().isNotFound());
    }

    @Test
    void update_shrinkingABuyBelowALaterSale_isRejected() throws Exception {
        long buy = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(10));
        recordTransaction(juan, jubilacion, "YPFD", "SELL", "8", "45000", daysAgo(3));

        updateTransaction(buy, jubilacion, "YPFD", "BUY", "5", "41500", daysAgo(10), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(leavesUncovered("8 YPFD", daysAgo(3), "Jubilación")))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());

        getTransaction(buy).andExpect(jsonPath("$.quantity").value("10")); // no se guardó nada
    }

    @Test
    void update_movingABuyAfterItsSale_isRejected() throws Exception {
        long buy = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(10));
        recordTransaction(juan, jubilacion, "YPFD", "SELL", "8", "45000", daysAgo(5));

        updateTransaction(buy, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(2), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(leavesUncovered("8 YPFD", daysAgo(5), "Jubilación")));
    }

    @Test
    void update_turningABuyIntoASale_isAllowedIfTheHoldingCoversIt() throws Exception {
        recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(10));
        long second = recordTransaction(juan, jubilacion, "YPFD", "BUY", "2", "42000", daysAgo(5));

        updateTransaction(second, jubilacion, "YPFD", "SELL", "2", "42000", daysAgo(5), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SELL"));

        mockMvc.perform(get("/api/v1/holdings/{symbol}", "YPFD").with(juan))
                .andExpect(jsonPath("$.quantity").value("8"));
    }

    @Test
    void update_ofSaleExceedingTheHolding_reportsItOnTheQuantityField() throws Exception {
        recordTransaction(juan, jubilacion, "BTC", "BUY", "0.5", "60000", daysAgo(10));
        long sale = recordTransaction(juan, jubilacion, "BTC", "SELL", "0.2", "65000", daysAgo(5));

        updateTransaction(sale, jubilacion, "BTC", "SELL", "0.6", "65000", daysAgo(5), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Supera tu tenencia (0,5)"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        updateTransaction(sale, jubilacion, "BTC", "SELL", "0.2", "65000", daysAgo(12), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Supera tu tenencia a esa fecha (0)"));

        updateTransaction(sale, jubilacion, "BTC", "SELL", "0.5", "65000", daysAgo(5), null)
                .andExpect(status().isOk());
    }

    @Test
    void update_movingABuyToAnotherPortfolio_checksThePortfolioItLeaves() throws Exception {
        long trading = createPortfolio(juan, "Trading");
        long buy = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(10));
        recordTransaction(juan, jubilacion, "YPFD", "SELL", "8", "45000", daysAgo(3));

        updateTransaction(buy, trading, "YPFD", "BUY", "10", "41500", daysAgo(10), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(leavesUncovered("8 YPFD", daysAgo(3), "Jubilación")));
    }

    @Test
    void update_movingToAnotherPortfolio_movesTheHolding() throws Exception {
        long trading = createPortfolio(juan, "Trading");
        long buy = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(10));

        updateTransaction(buy, trading, "YPFD", "BUY", "10", "41500", daysAgo(10), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolio.id").value(trading))
                .andExpect(jsonPath("$.portfolio.name").value("Trading"));

        mockMvc.perform(get("/api/v1/holdings/{symbol}", "YPFD").param("portfolioId", String.valueOf(trading)).with(juan))
                .andExpect(jsonPath("$.quantity").value("10"));
        mockMvc.perform(get("/api/v1/holdings/{symbol}", "YPFD").param("portfolioId", String.valueOf(jubilacion))
                        .with(juan))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_movingASaleToAPortfolioWithoutHolding_isRejected() throws Exception {
        long trading = createPortfolio(juan, "Trading");
        recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(10));
        long sale = recordTransaction(juan, jubilacion, "YPFD", "SELL", "4", "45000", daysAgo(3));

        updateTransaction(sale, trading, "YPFD", "SELL", "4", "45000", daysAgo(3), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Supera tu tenencia (0)"));
    }

    @Test
    void update_withSomethingThatIsNotTheUsers_isNotFound() throws Exception {
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5));
        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));
        long anasPortfolio = createPortfolio(ana, "De Ana");

        updateTransaction(id, anasPortfolio, "YPFD", "BUY", "10", "41500", daysAgo(5), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portafolio no encontrado"));
        updateTransaction(id, jubilacion, "NOEXISTE", "BUY", "10", "41500", daysAgo(5), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Activo no encontrado"));
        updateTransaction(ana, id, anasPortfolio, "YPFD", "BUY", "10", "41500", daysAgo(5), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Transacción no encontrada"));

        getTransaction(id).andExpect(jsonPath("$.portfolio.name").value("Jubilación"));
    }

    @Test
    void update_validatesTheFormLikeTheCreation() throws Exception {
        long id = recordTransaction(juan, jubilacion, "YPFD", "BUY", "10", "41500", daysAgo(5));
        LocalDate tomorrowInArgentina = LocalDate.now(ARGENTINA).plusDays(1);

        updateTransaction(id, jubilacion, "YPFD", "BUY", "0", "41500", tomorrowInArgentina, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", containsInAnyOrder("quantity", "tradeDate")));
    }

    // ---- Helpers ----

    private ResultActions getTransaction(long id) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions/{id}", id).with(juan));
    }

    private ResultActions deleteTransaction(long id) throws Exception {
        return mockMvc.perform(delete("/api/v1/transactions/{id}", id).with(juan));
    }

    private ResultActions updateTransaction(long id, long portfolioId, String symbol, String type, String quantity,
                                            String price, LocalDate date, String notes) throws Exception {
        return updateTransaction(juan, id, portfolioId, symbol, type, quantity, price, date, notes);
    }

    /** {@code PUT /transactions/{id}} con el formulario completo. {@code notes} en {@code null} = sin notas. */
    private ResultActions updateTransaction(RequestPostProcessor user, long id, long portfolioId, String symbol,
                                            String type, String quantity, String price, LocalDate date,
                                            String notes) throws Exception {
        String json = """
                {"portfolioId": %d, "assetSymbol": "%s", "type": "%s", "quantity": "%s",
                 "price": "%s", "tradeDate": "%s", "notes": %s}
                """.formatted(portfolioId, symbol, type, quantity, price, date,
                notes == null ? "null" : "\"" + notes + "\"");
        return mockMvc.perform(put("/api/v1/transactions/{id}", id).with(user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** "Este cambio deja sin tenencia suficiente a la venta de 8 YPFD del 20/09/2026 en Jubilación" */
    private static String leavesUncovered(String quantityAndSymbol, LocalDate date, String portfolio) {
        return "Este cambio deja sin tenencia suficiente a la venta de %s del %s en %s"
                .formatted(quantityAndSymbol, DAY.format(date), portfolio);
    }
}
