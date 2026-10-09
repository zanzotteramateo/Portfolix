package com.portfolix.api.portfolio;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Eliminar un portafolio moviendo o borrando sus transacciones.
 */
class PortfolioDeletionIntegrationTest extends ApiIntegrationTest {

    private RequestPostProcessor juan;
    private long jubilacion; // 2 transacciones de AAPL: tenencia 6
    private long ahorro;     // 1 compra de AAPL: tenencia 2

    @BeforeEach
    void setUp() throws Exception {
        juan = authenticatedAs(createUser("Juan Pérez"));
        jubilacion = createPortfolio(juan, "Jubilación");
        ahorro = createPortfolio(juan, "Ahorro");
        recordTransaction(juan, jubilacion, "AAPL", "BUY", "10", "11200", daysAgo(10));
        recordTransaction(juan, jubilacion, "AAPL", "SELL", "4", "12000", daysAgo(5));
        recordTransaction(juan, ahorro, "AAPL", "BUY", "2", "11500", daysAgo(3));
    }

    @Test
    void emptyPortfolio_canBeDeletedWithoutParameters() throws Exception {
        long empty = createPortfolio(juan, "Vacío");

        mockMvc.perform(delete("/api/v1/portfolios/{id}", empty).with(juan))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/portfolios/{id}", empty).with(juan))
                .andExpect(status().isNotFound());
    }

    @Test
    void portfolioWithTransactions_withoutChoosing_isRejectedAndKept() throws Exception {
        mockMvc.perform(delete("/api/v1/portfolios/{id}", jubilacion).with(juan))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("El portafolio tiene 2 transacciones: elegí moverlas a otro portafolio o eliminarlas"));

        mockMvc.perform(get("/api/v1/portfolios/{id}", jubilacion).with(juan))
                .andExpect(status().isOk());
        transactionsOf(jubilacion).andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void moveTransactions_keepsTheHistoryInTheTargetPortfolio() throws Exception {
        mockMvc.perform(delete("/api/v1/portfolios/{id}", jubilacion)
                        .param("moveTransactionsTo", String.valueOf(ahorro)).with(juan))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/portfolios/{id}", jubilacion).with(juan))
                .andExpect(status().isNotFound());
        transactionsOf(ahorro)
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].portfolio.name").value(everyItem(is("Ahorro"))));

        // La tenencia movida (6) se suma a la del destino (2): ahora se pueden vender 8 desde "Ahorro".
        recordTransaction(juan, ahorro, "AAPL", "SELL", "8", "12500", yesterday());
    }

    @Test
    void deleteTransactions_removesThePortfolioAndItsHistory() throws Exception {
        mockMvc.perform(delete("/api/v1/portfolios/{id}", jubilacion)
                        .param("deleteTransactions", "true").with(juan))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/portfolios/{id}", jubilacion).with(juan))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/transactions").with(juan))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].portfolio.name").value("Ahorro"));
    }

    @Test
    void invalidChoices_areRejectedWithoutChangingAnything() throws Exception {
        mockMvc.perform(delete("/api/v1/portfolios/{id}", jubilacion)
                        .param("moveTransactionsTo", String.valueOf(jubilacion)).with(juan))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("moveTransactionsTo"))
                .andExpect(jsonPath("$.message").value("Elegí un portafolio distinto al que vas a eliminar"));

        mockMvc.perform(delete("/api/v1/portfolios/{id}", jubilacion)
                        .param("moveTransactionsTo", String.valueOf(ahorro))
                        .param("deleteTransactions", "true").with(juan))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Elegí mover las transacciones o eliminarlas, no las dos cosas"));

        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));
        long anasPortfolio = createPortfolio(ana, "De Ana");
        mockMvc.perform(delete("/api/v1/portfolios/{id}", jubilacion)
                        .param("moveTransactionsTo", String.valueOf(anasPortfolio)).with(juan))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portafolio de destino no encontrado"));

        transactionsOf(jubilacion).andExpect(jsonPath("$.page.totalElements").value(2));
    }

    @Test
    void anotherUsersPortfolio_cannotBeDeleted() throws Exception {
        RequestPostProcessor ana = authenticatedAs(createUser("Ana Gómez"));

        mockMvc.perform(delete("/api/v1/portfolios/{id}", jubilacion)
                        .param("deleteTransactions", "true").with(ana))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portafolio no encontrado"));

        transactionsOf(jubilacion).andExpect(jsonPath("$.page.totalElements").value(2));
    }

    private ResultActions transactionsOf(long portfolioId) throws Exception {
        return mockMvc.perform(get("/api/v1/transactions").param("portfolioId", String.valueOf(portfolioId)).with(juan))
                .andExpect(status().isOk());
    }
}
