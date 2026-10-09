package com.portfolix.api.security.ratelimit;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El rate limit sobre los endpoints reales, con los límites de application.yml. Los demás tests lo apagan
 * (todos sus pedidos vienen de 127.0.0.1); acá se prende, y por eso esta clase levanta su propio contexto.
 * Cada test usa una IP distinta para no gastar el límite de otro.
 */
@TestPropertySource(properties = "portfolix.rate-limit.enabled=true")
class RateLimitIntegrationTest extends ApiIntegrationTest {

    @Test
    void login_isLimitedPerIp_andOtherIpsAreNotAffected() throws Exception {
        // 10 por minuto. Cada intento usa un mail distinto, para no mezclarlo con el bloqueo por intentos.
        for (int i = 0; i < 10; i++) {
            login("10.0.0.1").andExpect(status().isUnauthorized());
        }

        login("10.0.0.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(startsWith("Hiciste demasiados pedidos seguidos")));
        login("10.0.0.2").andExpect(status().isUnauthorized());
    }

    @Test
    void theEndpointsThatSendMails_shareOneLimit() throws Exception {
        // 10 por hora entre registro, reenvío, olvidé mi contraseña y cambio de mail.
        for (int i = 0; i < 10; i++) {
            postJson("/api/v1/auth/password/forgot", "{\"email\": \"%s\"}".formatted(uniqueEmail()), "10.0.0.3")
                    .andExpect(status().isAccepted());
        }

        postJson("/api/v1/auth/verify-email/resend", "{\"email\": \"%s\"}".formatted(uniqueEmail()), "10.0.0.3")
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void refresh_isNotLimited() throws Exception {
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/v1/auth/refresh").with(fromIp("10.0.0.4")))
                    .andExpect(status().isUnauthorized());
        }
    }

    private ResultActions login(String ip) throws Exception {
        String json = """
                {"email": "%s", "password": "Incorrecta1!"}
                """.formatted(uniqueEmail());
        return postJson("/api/v1/auth/login", json, ip);
    }

    private ResultActions postJson(String path, String json, String ip) throws Exception {
        return mockMvc.perform(post(path).with(fromIp(ip)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
