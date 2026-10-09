package com.portfolix.api.user.preference;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PreferencesIntegrationTest extends ApiIntegrationTest {

    @Test
    void aUserThatNeverChangedAnything_getsTheDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/me/preferences").with(authenticatedAs(createUser("Juan Pérez"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("LIGHT"))
                .andExpect(jsonPath("$.decimalSeparator").value("COMMA"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.hideAmounts").value(false));
    }

    @Test
    void patch_changesOnlyWhatComes_andIsRemembered() throws Exception {
        RequestPostProcessor juan = authenticatedAs(createUser("Juan Pérez"));

        update(juan, "{\"theme\": \"DARK\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.decimalSeparator").value("COMMA"));
        update(juan, "{\"decimalSeparator\": \"PERIOD\", \"hideAmounts\": true}")
                .andExpect(jsonPath("$.theme").value("DARK"));

        mockMvc.perform(get("/api/v1/me/preferences").with(juan))
                .andExpect(jsonPath("$.theme").value("DARK"))
                .andExpect(jsonPath("$.decimalSeparator").value("PERIOD"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.hideAmounts").value(true));
    }

    @Test
    void eachUserHasTheirOwnPreferences() throws Exception {
        update(authenticatedAs(createUser("Juan Pérez")), "{\"theme\": \"DARK\", \"currency\": \"USD\"}")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me/preferences").with(authenticatedAs(createUser("Ana López"))))
                .andExpect(jsonPath("$.theme").value("LIGHT"))
                .andExpect(jsonPath("$.currency").value("ARS"));
    }

    @Test
    void patch_withAnUnknownValue_returns400() throws Exception {
        update(authenticatedAs(createUser("Juan Pérez")), "{\"theme\": \"AZUL\"}")
                .andExpect(status().isBadRequest());
    }

    private ResultActions update(RequestPostProcessor user, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/me/preferences").with(user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
