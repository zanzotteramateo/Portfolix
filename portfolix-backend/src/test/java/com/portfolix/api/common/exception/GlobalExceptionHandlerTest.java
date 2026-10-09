package com.portfolix.api.common.exception;

import com.portfolix.api.security.JsonSecurityErrorHandler;
import com.portfolix.api.security.SecurityConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class,
        properties = "portfolix.cors.allowed-origins=http://localhost:5173")
@Import({SecurityConfig.class, JsonSecurityErrorHandler.class, GlobalExceptionHandlerTest.TestController.class})
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    // SecurityConfig valida JWT; en este test la autenticación la simula @WithMockUser.
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @WithAnonymousUser
    void withoutAuthentication_returns401WithErrorResponse() throws Exception {
        mockMvc.perform(get("/api/v1/test/business"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Tenés que iniciar sesión para continuar"))
                .andExpect(jsonPath("$.path").value("/api/v1/test/business"));
    }

    @Test
    void serviceUnavailableException_returns503() throws Exception {
        mockMvc.perform(get("/api/v1/test/unavailable"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("No pudimos obtener las cotizaciones. Probá de nuevo en unos minutos"));
    }

    @Test
    void unauthorizedException_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    void forbiddenException_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Tenés que verificar tu correo antes de iniciar sesión"));
    }

    @Test
    void tooManyRequestsException_returns429WithRetryAfterInWholeSeconds() throws Exception {
        mockMvc.perform(get("/api/v1/test/too-many"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Hiciste demasiados pedidos seguidos. Probá de nuevo en 5 segundos"));
    }

    @Test
    void corsResponse_letsTheFrontendReadRetryAfter() throws Exception {
        mockMvc.perform(get("/api/v1/test/too-many").header("Origin", "http://localhost:5173"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
    }

    @Test
    void resourceNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Portafolio no encontrado"))
                .andExpect(jsonPath("$.path").value("/api/v1/test/not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @Test
    void businessException_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("No podés vender más de lo que tenés"));
    }

    @Test
    void invalidBody_returns400WithFieldErrorsInSpanish() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(1)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("no debe estar vacío"));
    }

    @Test
    void invalidPathVariable_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(get("/api/v1/test/items/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("id"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud es inválido o está mal formado"));
    }

    @Test
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El parámetro 'id' tiene un formato inválido"));
    }

    @Test
    void unknownRoute_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El recurso solicitado no existe"));
    }

    @Test
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(delete("/api/v1/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void unexpectedError_returns500WithoutInternalDetails() throws Exception {
        mockMvc.perform(get("/api/v1/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Ocurrió un error inesperado. Intentá de nuevo más tarde"));
    }

    @Test
    void corsPreflight_fromFrontend_isAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/test/business")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void corsPreflight_fromUnknownOrigin_isRejected() throws Exception {
        mockMvc.perform(options("/api/v1/test/business")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    /** Controller que solo existe en los tests para disparar cada tipo de error. */
    @RestController
    @RequestMapping("/api/v1/test")
    static class TestController {

        record TestRequest(@NotBlank String name) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Portafolio no encontrado");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException("No podés vender más de lo que tenés");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/items/{id}")
        void item(@PathVariable @Positive Long id) {
        }

        @GetMapping("/unauthorized")
        void unauthorized() {
            throw new UnauthorizedException("Correo o contraseña incorrectos");
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new ForbiddenException("Tenés que verificar tu correo antes de iniciar sesión");
        }

        @GetMapping("/too-many")
        void tooMany() {
            throw new TooManyRequestsException("Hiciste demasiados pedidos seguidos. Probá de nuevo en 5 segundos",
                    Duration.ofMillis(4500));
        }

        @GetMapping("/unavailable")
        void unavailable() {
            throw new ServiceUnavailableException("No pudimos obtener las cotizaciones. Probá de nuevo en unos minutos");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("detalle interno que no debe filtrarse");
        }
    }
}
