package com.portfolix.api.auth;

import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.ApiIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recorre el flujo completo de autenticación contra la app real y un Postgres de Testcontainers.
 * A diferencia del resto de los tests de integración, hace el login de verdad (no usa {@code jwt()}).
 * La verificación del mail se prueba en detalle en EmailVerificationIntegrationTest.
 */
class AuthFlowIntegrationTest extends ApiIntegrationTest {

    private static final String COOKIE = "portfolix_refresh";

    @Test
    void fullFlow_register_verify_login_me_refresh_logout() throws Exception {
        String email = uniqueEmail();

        // Registro: 201 con los datos del usuario, sin sesión.
        register("Juan Pérez", email.toUpperCase(), PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Juan Pérez"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        // Verificación con el link del mail (llega al mail ya normalizado).
        verifyEmail(mailbox.awaitMail(email).token()).andExpect(status().isNoContent());

        // Login con "Recuérdame": access token en el body, refresh token en cookie persistente.
        MvcResult login = login(email, PASSWORD, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=2592000")))
                .andReturn();
        String accessToken = accessToken(login);
        String refreshToken = refreshCookie(login);

        // /me con el access token.
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        // Refresh: nuevo access token y nuevo refresh token (rotación).
        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();
        String rotatedRefreshToken = refreshCookie(refresh);
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        // Logout: revoca el token y borra la cookie.
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie(COOKIE, rotatedRefreshToken)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // Después del logout el refresh token ya no sirve.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, rotatedRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reusingARotatedRefreshToken_closesAllSessions() throws Exception {
        String email = registerVerifiedUser();
        String firstToken = refreshCookie(login(email, PASSWORD, false).andReturn());

        String secondToken = refreshCookie(mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new Cookie(COOKIE, firstToken))).andReturn());

        // Alguien reusa el token viejo (ya rotado): se rechaza...
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, firstToken)))
                .andExpect(status().isUnauthorized());
        // ...y también queda revocado el token legítimo más nuevo.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie(COOKIE, secondToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutRememberMe_usesSessionCookie() throws Exception {
        String email = registerVerifiedUser();

        login(email, PASSWORD, false)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Max-Age"))));
    }

    @Test
    void login_withWrongPasswordOrUnknownEmail_returnsSameGenericError() throws Exception {
        String email = registerVerifiedUser();

        login(email, "Incorrecta1!", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
        login(uniqueEmail(), PASSWORD, false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    void register_withDuplicateEmail_returns400() throws Exception {
        String email = registerVerifiedUser();

        register("Otro Juan", email, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con este correo"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email")); // el front lo muestra en el campo
    }

    @Test
    void register_withWeakPasswordAndWithoutTerms_returnsFieldErrors() throws Exception {
        String json = """
                {"fullName": "Juan Pérez", "email": "%s", "password": "debil", "acceptedTerms": false}
                """.formatted(uniqueEmail());

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'acceptedTerms')].message")
                        .value("Tenés que aceptar los términos y condiciones"));
    }

    @Test
    void login_withoutRememberMeField_worksAsSessionLogin() throws Exception {
        String email = registerVerifiedUser();
        String json = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, not(containsString("Max-Age"))));
    }

    @Test
    void register_withoutAcceptedTermsField_returnsFieldError() throws Exception {
        String json = """
                {"fullName": "Juan Pérez", "email": "%s", "password": "%s"}
                """.formatted(uniqueEmail(), PASSWORD);

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("acceptedTerms"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Tenés que aceptar los términos y condiciones"));
    }

    @Test
    void protectedEndpoint_withoutOrWithInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer token-inventado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refresh_withoutCookie_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerDocs_arePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists());
    }

    // ---- helpers ----

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("cookie del refresh token").isNotNull();
        return cookie.getValue();
    }
}
