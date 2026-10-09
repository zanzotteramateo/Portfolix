package com.portfolix.api.portfolio;

import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRUD de portafolios contra la app real. El borrado con transacciones se prueba en PortfolioDeletionIntegrationTest.
 */
class PortfolioIntegrationTest extends ApiIntegrationTest {

    private RequestPostProcessor juan;
    private RequestPostProcessor ana;

    @BeforeEach
    void setUp() {
        juan = authenticatedAs(createUser("Juan Pérez"));
        ana = authenticatedAs(createUser("Ana Gómez"));
    }

    @Test
    void crud_create_list_get_rename_delete() throws Exception {
        MvcResult created = postPortfolio(juan, "  Jubilación  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jubilación"))
                .andReturn();
        Long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(created.getResponse().getHeader("Location")).endsWith("/api/v1/portfolios/" + id);

        mockMvc.perform(get("/api/v1/portfolios/{id}", id).with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jubilación"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(send(patch("/api/v1/portfolios/{id}", id), "Jubilación 2050").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jubilación 2050"));

        mockMvc.perform(delete("/api/v1/portfolios/{id}", id).with(juan))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/portfolios/{id}", id).with(juan))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Portafolio no encontrado"));
    }

    @Test
    void list_isOrderedByCreationAndOnlyShowsOwnPortfolios() throws Exception {
        long first = createPortfolio(juan, "Jubilación");
        createPortfolio(juan, "Trading a corto plazo");
        createPortfolio(juan, "Ahorro Crypto");
        createPortfolio(ana, "Portafolio de Ana");

        // Renombrar no cambia el orden.
        mockMvc.perform(send(patch("/api/v1/portfolios/{id}", first), "Zeta").with(juan))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/portfolios").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Zeta"))
                .andExpect(jsonPath("$[1].name").value("Trading a corto plazo"))
                .andExpect(jsonPath("$[2].name").value("Ahorro Crypto"));
    }

    @Test
    void anotherUsersPortfolio_behavesAsNonExistent() throws Exception {
        long juansPortfolio = createPortfolio(juan, "Jubilación");

        mockMvc.perform(get("/api/v1/portfolios/{id}", juansPortfolio).with(ana))
                .andExpect(status().isNotFound());
        mockMvc.perform(send(patch("/api/v1/portfolios/{id}", juansPortfolio), "Hackeado").with(ana))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/portfolios/{id}", juansPortfolio).with(ana))
                .andExpect(status().isNotFound());

        // Sigue intacto para Juan.
        mockMvc.perform(get("/api/v1/portfolios/{id}", juansPortfolio).with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jubilación"));
    }

    @Test
    void duplicateName_isRejectedIgnoringCase_butAllowedForAnotherUser() throws Exception {
        postPortfolio(juan, "Jubilación").andExpect(status().isCreated());

        postPortfolio(juan, "JUBILACIÓN")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya tenés un portafolio con ese nombre"));
        postPortfolio(ana, "Jubilación").andExpect(status().isCreated());
    }

    @Test
    void rename_onlyChangingCase_isAllowed() throws Exception {
        long id = createPortfolio(juan, "jubilación");

        mockMvc.perform(send(patch("/api/v1/portfolios/{id}", id), "Jubilación").with(juan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jubilación"));
    }

    @Test
    void invalidNames_returnFieldErrors() throws Exception {
        postPortfolio(juan, "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Ingresá un nombre para el portafolio"));
        postPortfolio(juan, "x".repeat(51))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("El nombre puede tener hasta 50 caracteres"));
    }

    @Test
    void cannotCreateMoreThan20Portfolios() throws Exception {
        for (int i = 1; i <= 20; i++) {
            postPortfolio(juan, "Portafolio " + i).andExpect(status().isCreated());
        }

        postPortfolio(juan, "Portafolio 21")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Alcanzaste el máximo de 20 portafolios"));
    }

    @Test
    void withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/portfolios")).andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    /** POST sin verificar el resultado, para probar también los casos de error. */
    private ResultActions postPortfolio(RequestPostProcessor user, String name) throws Exception {
        return mockMvc.perform(send(post("/api/v1/portfolios"), name).with(user));
    }

    private static MockHttpServletRequestBuilder send(MockHttpServletRequestBuilder request, String name) {
        return request.contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\"}".formatted(name));
    }
}
