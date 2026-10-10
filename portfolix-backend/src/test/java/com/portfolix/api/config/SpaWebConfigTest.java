package com.portfolix.api.config;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El front ya compilado (deploy de un solo origen, fase 12): acá solo hay un {@code index.html} de
 * prueba (src/test/resources/static), para probar el cableado sin depender del build real del front.
 */
class SpaWebConfigTest extends ApiIntegrationTest {

    @Test
    void unaRutaDelFrontDevuelveIndexHtml() throws Exception {
        mockMvc.perform(get("/transactions"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("front de prueba")));
    }

    @Test
    void laRaizTambienSirveElFront() throws Exception {
        // La raíz la resuelve la "welcome page" de Spring Boot (detecta static/index.html sola), que
        // reenvía a index.html en vez de devolver el contenido directo: MockMvc no simula ese reenvío,
        // así que acá solo confirmamos que apunta bien (el contenido real se prueba en /transactions).
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void unaRutaDeApiQueNoExisteSigueSiendo404() throws Exception {
        Long userId = createUser("Ana");
        mockMvc.perform(get("/api/v1/no-existe").with(authenticatedAs(userId)))
                .andExpect(status().isNotFound());
    }
}
