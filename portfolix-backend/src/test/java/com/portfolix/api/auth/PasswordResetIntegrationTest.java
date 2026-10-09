package com.portfolix.api.auth;

import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.TestMailbox.ReceivedMail;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** "¿Olvidaste tu contraseña?" de punta a punta: los mails se leen del TestMailbox. */
class PasswordResetIntegrationTest extends ApiIntegrationTest {

    private static final String NEW_PASSWORD = "NuevaClave2026!";

    @Test
    void forgotAndReset_changeThePassword_andCloseAllSessions() throws Exception {
        String email = registerVerifiedUser(); // 1er mail: la verificación
        Cookie session = login(email, PASSWORD, true).andReturn().getResponse().getCookie("portfolix_refresh");

        forgot(email).andExpect(status().isAccepted());
        ReceivedMail mail = mailbox.awaitMail(email, 2);
        assertThat(mail.subject()).isEqualTo("Restablecé tu contraseña de Portfolix");
        assertThat(mail.html())
                .contains("http://localhost:5173/reset-password?token=" + mail.token())
                .contains("1 hora");

        reset(mail.token(), NEW_PASSWORD).andExpect(status().isNoContent());

        // La sesión que estaba abierta se cerró.
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(session)).andExpect(status().isUnauthorized());
        login(email, PASSWORD, false).andExpect(status().isUnauthorized());
        login(email, NEW_PASSWORD, false).andExpect(status().isOk());

        // El link sirve una sola vez.
        reset(mail.token(), "OtraClave2026!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"))
                .andExpect(jsonPath("$.message").value("Este link ya se usó. Si lo necesitás, pedí uno nuevo"));
    }

    @Test
    void reset_withTheCurrentPassword_isRejected_andTheLinkStillWorks() throws Exception {
        String email = registerVerifiedUser();
        forgot(email).andExpect(status().isAccepted());
        String token = mailbox.awaitMail(email, 2).token();

        reset(token, PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("La contraseña nueva tiene que ser distinta de la actual"));

        // El error deshizo toda la operación: el link no quedó gastado.
        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void reset_withWeakPassword_returnsFieldError() throws Exception {
        String email = registerVerifiedUser();
        forgot(email).andExpect(status().isAccepted());
        String token = mailbox.awaitMail(email, 2).token();

        reset(token, "debil")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
    }

    @Test
    void reset_onAnUnverifiedAccount_alsoVerifiesIt() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());
        forgot(email).andExpect(status().isAccepted());
        String token = mailbox.awaitMail(email, 2).token();

        reset(token, NEW_PASSWORD).andExpect(status().isNoContent());

        // Usar el link probó que es el dueño del mail: ya puede entrar sin el link de verificación.
        login(email, NEW_PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void forgot_withUnknownEmail_answersTheSameWithoutSendingMails() throws Exception {
        String unknown = uniqueEmail();

        forgot(unknown).andExpect(status().isAccepted());

        mailbox.assertNoNewMail(unknown, 0);
    }

    @Test
    void forgot_twiceInAMinute_sendsOnlyOneMail() throws Exception {
        String email = registerVerifiedUser();
        forgot(email).andExpect(status().isAccepted());
        mailbox.awaitMail(email, 2);

        forgot(email).andExpect(status().isAccepted());

        mailbox.assertNoNewMail(email, 2);
    }

    @Test
    void reset_withUnknownToken_returns400OnTokenField() throws Exception {
        reset("token-inventado", NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"));
    }

    private ResultActions forgot(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions reset(String token, String newPassword) throws Exception {
        String json = """
                {"token": "%s", "newPassword": "%s"}
                """.formatted(token, newPassword);
        return mockMvc.perform(post("/api/v1/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
