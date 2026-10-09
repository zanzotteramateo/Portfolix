package com.portfolix.api.portfolio;

import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortfolioDuplicationIntegrationTest extends ApiIntegrationTest {

    @Test
    void duplicate_copiesTheTransactions_andTheCopyIsIndependent() throws Exception {
        RequestPostProcessor juan = authenticatedAs(createUser("Juan Pérez"));
        long original = createPortfolio(juan, "Jubilación");
        recordTransaction(juan, original, "AAPL", "BUY", "10", "11000", daysAgo(20));
        recordTransaction(juan, original, "AAPL", "BUY", "10", "12000", daysAgo(10));
        recordTransaction(juan, original, "AAPL", "SELL", "5", "13000", daysAgo(5));

        MvcResult result = duplicate(juan, original, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jubilación (copia)"))
                .andReturn();
        long copy = idOf(result);
        header().string(HttpHeaders.LOCATION, endsWith("/api/v1/portfolios/" + copy)).match(result);

        mockMvc.perform(get("/api/v1/transactions").param("portfolioId", String.valueOf(copy)).with(juan))
                .andExpect(jsonPath("$.page.totalElements").value(3));
        holding(juan, copy).andExpect(jsonPath("$.quantity").value("15"));

        // Vender todo en la copia no toca el original.
        recordTransaction(juan, copy, "AAPL", "SELL", "15", "13000", yesterday());
        holding(juan, original).andExpect(jsonPath("$.quantity").value("15"));
    }

    @Test
    void duplicatingAgain_numbersTheCopies() throws Exception {
        RequestPostProcessor juan = authenticatedAs(createUser("Juan Pérez"));
        long original = createPortfolio(juan, "Jubilación");
        duplicate(juan, original, null).andExpect(status().isCreated());

        duplicate(juan, original, null).andExpect(jsonPath("$.name").value("Jubilación (copia 2)"));
    }

    @Test
    void duplicate_withAName_usesIt_butItCannotBeRepeated() throws Exception {
        RequestPostProcessor juan = authenticatedAs(createUser("Juan Pérez"));
        long original = createPortfolio(juan, "Jubilación");

        duplicate(juan, original, "{\"name\": \"  Escenario optimista  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Escenario optimista"));
        duplicate(juan, original, "{\"name\": \"escenario OPTIMISTA\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya tenés un portafolio con ese nombre"));
    }

    @Test
    void duplicate_withABlankName_returns400() throws Exception {
        RequestPostProcessor juan = authenticatedAs(createUser("Juan Pérez"));

        duplicate(juan, createPortfolio(juan, "Jubilación"), "{\"name\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void duplicate_ofAnotherUsersPortfolio_returns404() throws Exception {
        long anasPortfolio = createPortfolio(authenticatedAs(createUser("Ana López")), "Jubilación");

        duplicate(authenticatedAs(createUser("Juan Pérez")), anasPortfolio, null)
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicate_whenAtTheLimitOfPortfolios_returns400() throws Exception {
        RequestPostProcessor juan = authenticatedAs(createUser("Juan Pérez"));
        long first = createPortfolio(juan, "Portafolio 1");
        for (int i = 2; i <= 20; i++) {
            createPortfolio(juan, "Portafolio " + i);
        }

        duplicate(juan, first, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Alcanzaste el máximo de 20 portafolios"));
    }

    private ResultActions duplicate(RequestPostProcessor user, long portfolioId, String json) throws Exception {
        var request = post("/api/v1/portfolios/{id}/duplicate", portfolioId).with(user);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private ResultActions holding(RequestPostProcessor user, long portfolioId) throws Exception {
        return mockMvc.perform(get("/api/v1/holdings/{symbol}", "AAPL")
                        .param("portfolioId", String.valueOf(portfolioId)).with(user))
                .andExpect(status().isOk());
    }

    private static long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
