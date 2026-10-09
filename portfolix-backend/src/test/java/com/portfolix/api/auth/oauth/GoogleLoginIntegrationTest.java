package com.portfolix.api.auth.oauth;

import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.TestGoogle;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Continuar con Google" de punta a punta. Los ID tokens los firma TestGoogle con una clave de prueba:
 * la validación (firma, emisor, destinatario, vencimiento) se prueba en GoogleIdTokenVerifierTest.
 */
class GoogleLoginIntegrationTest extends ApiIntegrationTest {

    @Test
    void firstLogin_createsAVerifiedAccountWithoutPassword_thatCanCreateOneLater() throws Exception {
        String email = uniqueEmail();

        String accessToken = accessToken(googleLogin(TestGoogle.idToken(newSubject(), email), false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer")));
        me(accessToken)
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.fullName").value("Juan Pérez"));

        // No tiene contraseña: el login con contraseña responde el error genérico...
        login(email, PASSWORD, false).andExpect(status().isUnauthorized());
        // ...hasta que se crea una con "Olvidé mi contraseña".
        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email))).andExpect(status().isAccepted());
        String token = mailbox.awaitMail(email).token();
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"%s\"}".formatted(token, PASSWORD)))
                .andExpect(status().isNoContent());
        login(email, PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void withTheEmailOfAVerifiedAccount_entersThatAccount_andThePasswordKeepsWorking() throws Exception {
        String email = registerVerifiedUser();
        long accountId = userService.findByEmail(email).orElseThrow().getId();

        String accessToken = accessToken(googleLogin(TestGoogle.idToken(newSubject(), email), false)
                .andExpect(status().isOk()));

        me(accessToken).andExpect(jsonPath("$.id").value(accountId));
        login(email, PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void withTheEmailOfAnUnverifiedAccount_verifiesIt_andDiscardsThePassword() throws Exception {
        // Alguien registró este mail con su contraseña, pero nunca probó ser el dueño.
        String email = uniqueEmail();
        register("Otra persona", email, PASSWORD).andExpect(status().isCreated());

        googleLogin(TestGoogle.idToken(newSubject(), email), false).andExpect(status().isOk());

        // Esa contraseña ya no sirve: la cuenta es de quien probó tener el mail (con Google).
        login(email, PASSWORD, false).andExpect(status().isUnauthorized());
    }

    @Test
    void theSameGoogleAccount_isRecognizedBySubject_evenIfItsEmailChanged() throws Exception {
        String subject = newSubject();
        String originalEmail = uniqueEmail();
        googleLogin(TestGoogle.idToken(subject, originalEmail), false).andExpect(status().isOk());

        String accessToken = accessToken(googleLogin(TestGoogle.idToken(subject, uniqueEmail()), false)
                .andExpect(status().isOk()));

        me(accessToken).andExpect(jsonPath("$.email").value(originalEmail));
    }

    @Test
    void anotherGoogleAccountWithTheEmailOfAnAlreadyLinkedAccount_isRejected() throws Exception {
        String email = uniqueEmail();
        googleLogin(TestGoogle.idToken(newSubject(), email), false).andExpect(status().isOk());

        googleLogin(TestGoogle.idToken(newSubject(), email), false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(OAuthAccountService.OTHER_ACCOUNT_LINKED_MESSAGE));
    }

    @Test
    void invalidToken_returns401() throws Exception {
        googleLogin(TestGoogle.idTokenSignedWithAnotherKey(newSubject(), uniqueEmail()), false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(GoogleIdTokenVerifier.INVALID_TOKEN_MESSAGE));
    }

    @Test
    void emailNotVerifiedByGoogle_returns403() throws Exception {
        String token = TestGoogle.idToken(newSubject(), uniqueEmail(), claims -> claims.claim("email_verified", false));

        googleLogin(token, false).andExpect(status().isForbidden());
    }

    @Test
    void rememberMe_opensAPersistentSession() throws Exception {
        googleLogin(TestGoogle.idToken(newSubject(), uniqueEmail()), true)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=2592000")));
    }

    private ResultActions googleLogin(String idToken, boolean rememberMe) throws Exception {
        String json = """
                {"idToken": "%s", "rememberMe": %s}
                """.formatted(idToken, rememberMe);
        return mockMvc.perform(post("/api/v1/auth/oauth/google").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mockMvc.perform(get("/api/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    private static String accessToken(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.accessToken");
    }

    /** El {@code sub} de una cuenta de Google nueva. */
    private static String newSubject() {
        return UUID.randomUUID().toString();
    }
}
