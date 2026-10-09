package com.portfolix.api.account;

import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.TestGoogle;
import com.portfolix.api.TestMailbox.ReceivedMail;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cuenta del usuario de punta a punta, con sesiones reales (login de verdad, no {@code jwt()}): así el access
 * token trae el {@code sid} y se puede comprobar qué sesiones se cierran.
 */
class AccountIntegrationTest extends ApiIntegrationTest {

    private static final String COOKIE = "portfolix_refresh";
    private static final String NEW_PASSWORD = "NuevaClave2026!";

    // ---- /me ----

    @Test
    void me_tellsWhetherTheAccountHasAPassword() throws Exception {
        me(loginSession(registerVerifiedUser()).accessToken())
                .andExpect(jsonPath("$.hasPassword").value(true));

        me(googleOnlyAccessToken())
                .andExpect(jsonPath("$.hasPassword").value(false));
    }

    // ---- Cambio de contraseña ----

    @Test
    void changePassword_keepsThisSession_andClosesTheOthers() throws Exception {
        String email = registerVerifiedUser();
        Session thisDevice = loginSession(email);
        Session otherDevice = loginSession(email);

        changePassword(thisDevice.accessToken(), PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        refresh(thisDevice.refreshCookie()).andExpect(status().isOk());
        refresh(otherDevice.refreshCookie()).andExpect(status().isUnauthorized());
        login(email, PASSWORD, false).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void changePassword_withWrongCurrentPassword_returns400OnThatField() throws Exception {
        String accessToken = loginSession(registerVerifiedUser()).accessToken();

        changePassword(accessToken, "Incorrecta1!", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"))
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
    }

    @Test
    void changePassword_toTheCurrentOne_isRejected() throws Exception {
        String accessToken = loginSession(registerVerifiedUser()).accessToken();

        changePassword(accessToken, PASSWORD, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    @Test
    void changePassword_fiveWrongAttempts_lockTheAccount() throws Exception {
        String email = registerVerifiedUser();
        String accessToken = loginSession(email).accessToken();
        for (int i = 0; i < 4; i++) {
            changePassword(accessToken, "Incorrecta1!", NEW_PASSWORD).andExpect(status().isBadRequest());
        }

        changePassword(accessToken, "Incorrecta1!", NEW_PASSWORD).andExpect(status().isTooManyRequests());

        // Es el mismo bloqueo del login.
        login(email, PASSWORD, false).andExpect(status().isTooManyRequests());
    }

    @Test
    void changePassword_onAGoogleOnlyAccount_asksToCreateAPasswordFirst() throws Exception {
        changePassword(googleOnlyAccessToken(), "lo-que-sea", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Tu cuenta no tiene contraseña")));
    }

    // ---- Cambio de mail ----

    @Test
    void emailChange_isAppliedOnlyWhenTheNewEmailConfirmsIt() throws Exception {
        String email = registerVerifiedUser();
        String accessToken = loginSession(email).accessToken();
        String newEmail = uniqueEmail();

        requestEmailChange(accessToken, newEmail, PASSWORD).andExpect(status().isAccepted());
        ReceivedMail mail = mailbox.awaitMail(newEmail);
        assertThat(mail.subject()).isEqualTo("Confirmá tu nuevo correo en Portfolix");
        assertThat(mail.html()).contains("http://localhost:5173/confirm-email-change?token=" + mail.token());

        // Hasta que se confirma, se sigue entrando con el mail actual.
        login(email, PASSWORD, false).andExpect(status().isOk());

        confirmEmailChange(mail.token()).andExpect(status().isNoContent());
        confirmEmailChange(mail.token()).andExpect(status().isNoContent()); // abrir dos veces el link no es un error

        me(accessToken).andExpect(jsonPath("$.email").value(newEmail));
        login(newEmail, PASSWORD, false).andExpect(status().isOk());
        login(email, PASSWORD, false).andExpect(status().isUnauthorized());
    }

    @Test
    void emailChange_toAnEmailWithAccount_returns400OnNewEmail() throws Exception {
        String accessToken = loginSession(registerVerifiedUser()).accessToken();

        requestEmailChange(accessToken, registerVerifiedUser(), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newEmail"))
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con este correo"));
    }

    @Test
    void emailChange_toTheSameEmail_returns400() throws Exception {
        String email = registerVerifiedUser();

        requestEmailChange(loginSession(email).accessToken(), email.toUpperCase(), PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Es el mismo correo que ya tenés"));
    }

    @Test
    void emailChange_withWrongPassword_returns400OnCurrentPassword() throws Exception {
        String accessToken = loginSession(registerVerifiedUser()).accessToken();

        requestEmailChange(accessToken, uniqueEmail(), "Incorrecta1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
    }

    @Test
    void emailChangeConfirmation_whenTheNewEmailWasTakenMeanwhile_returns400() throws Exception {
        String accessToken = loginSession(registerVerifiedUser()).accessToken();
        String newEmail = uniqueEmail();
        requestEmailChange(accessToken, newEmail, PASSWORD).andExpect(status().isAccepted());
        String token = mailbox.awaitMail(newEmail).token();

        // Mientras tanto, alguien se registra con ese mail.
        register("Otra persona", newEmail, PASSWORD).andExpect(status().isCreated());

        confirmEmailChange(token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ya existe una cuenta con este correo"));
    }

    @Test
    void confirmEmailChange_withUnknownToken_returns400OnTokenField() throws Exception {
        confirmEmailChange("token-inventado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"));
    }

    // ---- Eliminar cuenta ----

    @Test
    void deletionSummary_countsWhatWouldBeDeleted() throws Exception {
        String email = registerVerifiedUser();
        RequestPostProcessor user = authenticatedAs(userService.findByEmail(email).orElseThrow().getId());
        long retirement = createPortfolio(user, "Jubilación");
        long crypto = createPortfolio(user, "Ahorro Crypto");
        recordTransaction(user, retirement, "YPFD", "BUY", "10", "25000", daysAgo(5));
        recordTransaction(user, retirement, "AAPL", "BUY", "3", "15000", daysAgo(4));
        recordTransaction(user, crypto, "BTC", "BUY", "0.1", "60000", daysAgo(3));
        recordTransaction(user, retirement, "YPFD", "SELL", "5", "26000", daysAgo(2));

        mockMvc.perform(get("/api/v1/me/deletion-summary").with(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portfolios").value(2))
                .andExpect(jsonPath("$.assets").value(3))
                .andExpect(jsonPath("$.transactions").value(4))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void deleteAccount_erasesEverything_andTheEmailCanBeUsedAgain() throws Exception {
        String email = registerVerifiedUser();
        Long userId = userService.findByEmail(email).orElseThrow().getId();
        Session session = loginSession(email);
        createPortfolio(authenticatedAs(userId), "Jubilación");

        deleteAccount(session.accessToken(), PASSWORD)
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(userService.findByEmail(email)).isEmpty();
        refresh(session.refreshCookie()).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/portfolios").with(authenticatedAs(userId)))
                .andExpect(jsonPath("$", hasSize(0)));
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());
    }

    @Test
    void deleteAccount_withWrongPassword_deletesNothing() throws Exception {
        String email = registerVerifiedUser();

        deleteAccount(loginSession(email).accessToken(), "Incorrecta1!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));

        assertThat(userService.findByEmail(email)).isPresent();
    }

    @Test
    void deleteAccount_ofAGoogleOnlyAccount_asksToCreateAPasswordFirst() throws Exception {
        deleteAccount(googleOnlyAccessToken(), "lo-que-sea")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Tu cuenta no tiene contraseña")));
    }

    // ---- helpers ----

    /** Una sesión abierta con login: el access token (con su {@code sid}) y la cookie del refresh token. */
    private record Session(String accessToken, Cookie refreshCookie) {
    }

    private Session loginSession(String email) throws Exception {
        MvcResult result = login(email, PASSWORD, false).andExpect(status().isOk()).andReturn();
        String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        return new Session(accessToken, result.getResponse().getCookie(COOKIE));
    }

    /** El access token de una cuenta nueva que entra solo con Google (sin contraseña). */
    private String googleOnlyAccessToken() throws Exception {
        String json = "{\"idToken\": \"%s\"}".formatted(TestGoogle.idToken(UUID.randomUUID().toString(), uniqueEmail()));
        String body = mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private ResultActions refresh(Cookie refreshCookie) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie));
    }

    private ResultActions changePassword(String accessToken, String currentPassword, String newPassword)
            throws Exception {
        String json = """
                {"currentPassword": "%s", "newPassword": "%s"}
                """.formatted(currentPassword, newPassword);
        return mockMvc.perform(put("/api/v1/me/password").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions requestEmailChange(String accessToken, String newEmail, String currentPassword)
            throws Exception {
        String json = """
                {"newEmail": "%s", "currentPassword": "%s"}
                """.formatted(newEmail, currentPassword);
        return mockMvc.perform(post("/api/v1/me/email-change").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions confirmEmailChange(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    private ResultActions deleteAccount(String accessToken, String currentPassword) throws Exception {
        return mockMvc.perform(delete("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\": \"%s\"}".formatted(currentPassword)));
    }
}
