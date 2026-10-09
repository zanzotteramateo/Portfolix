package com.portfolix.api.auth;

import com.portfolix.api.ApiIntegrationTest;
import com.portfolix.api.TestMailbox.ReceivedMail;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verificación del mail de punta a punta. Los mails no se mandan: se leen del TestMailbox,
 * y el token se saca del link como lo haría la página del front.
 */
class EmailVerificationIntegrationTest extends ApiIntegrationTest {

    @Test
    void register_sendsVerificationMail_andLoginOnlyWorksAfterVerifying() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());

        ReceivedMail mail = mailbox.awaitMail(email);
        assertThat(mail.subject()).isEqualTo("Confirmá tu correo en Portfolix");
        assertThat(mail.html())
                .contains("Hola, <span>Juan Pérez</span>")
                .contains("http://localhost:5173/verify-email?token=" + mail.token())
                .contains("24 horas");

        // Con la contraseña correcta pero sin verificar: 403 (no 401), para que el front ofrezca reenviar el mail.
        login(email, PASSWORD, false)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(startsWith("Tenés que verificar tu correo")));

        verifyEmail(mail.token()).andExpect(status().isNoContent());

        login(email, PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void login_onUnverifiedAccountWithWrongPassword_returnsTheGeneric401() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());

        // El 403 solo se lo lleva quien conoce la contraseña: si no, no se sabe que la cuenta existe.
        login(email, "Incorrecta1!", false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    void verify_twiceWithTheSameLink_isNotAnError() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());
        String token = mailbox.awaitMail(email).token();

        verifyEmail(token).andExpect(status().isNoContent());
        verifyEmail(token).andExpect(status().isNoContent());
    }

    @Test
    void verify_withUnknownToken_returns400OnTokenField() throws Exception {
        verifyEmail("token-inventado")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("token"))
                .andExpect(jsonPath("$.message").value(startsWith("El link no es válido")));
    }

    @Test
    void resend_toPendingAccount_sendsAWorkingLink_atMostOncePerMinute() throws Exception {
        // Creada directamente con el service: todavía no recibió ningún mail.
        User user = userService.createUser("Juan Pérez", uniqueEmail(), PASSWORD);
        String email = user.getEmail();

        resend(email).andExpect(status().isAccepted());
        ReceivedMail mail = mailbox.awaitMail(email);

        // Un segundo pedido enseguida no manda otro mail: el link anterior sigue sirviendo.
        resend(email).andExpect(status().isAccepted());
        mailbox.assertNoNewMail(email, 1);

        verifyEmail(mail.token()).andExpect(status().isNoContent());
        login(email, PASSWORD, false).andExpect(status().isOk());
    }

    @Test
    void resend_toUnknownOrVerifiedEmail_answersTheSameWithoutSendingMails() throws Exception {
        String unknown = uniqueEmail();
        resend(unknown).andExpect(status().isAccepted());
        mailbox.assertNoNewMail(unknown, 0);

        String verified = registerVerifiedUser();
        resend(verified).andExpect(status().isAccepted());
        mailbox.assertNoNewMail(verified, 1);
    }

    @Test
    void resend_withInvalidEmail_returns400() throws Exception {
        resend("no-es-un-mail")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("email"));
    }

    @Test
    void changePendingEmail_movesTheAccountToTheNewEmail_andOnlyTheNewLinkWorks() throws Exception {
        String typo = uniqueEmail();
        String fixed = uniqueEmail();
        register("Juan Pérez", typo, PASSWORD).andExpect(status().isCreated());
        String oldToken = mailbox.awaitMail(typo).token();

        changePendingEmail(typo, PASSWORD, fixed).andExpect(status().isAccepted());
        String newToken = mailbox.awaitMail(fixed).token();

        verifyEmail(oldToken).andExpect(status().isBadRequest());
        verifyEmail(newToken).andExpect(status().isNoContent());
        login(fixed, PASSWORD, false).andExpect(status().isOk());
        login(typo, PASSWORD, false).andExpect(status().isUnauthorized());
    }

    @Test
    void changePendingEmail_withWrongPassword_returns401() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());

        changePendingEmail(email, "Incorrecta1!", uniqueEmail())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Correo o contraseña incorrectos"));
    }

    @Test
    void changePendingEmail_toAnEmailWithAccount_returns400OnNewEmailField() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());
        String taken = registerVerifiedUser();

        changePendingEmail(email, PASSWORD, taken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("newEmail"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Ya existe una cuenta con este correo"));
    }

    @Test
    void changePendingEmail_onVerifiedAccount_returns400() throws Exception {
        String email = registerVerifiedUser();

        changePendingEmail(email, PASSWORD, uniqueEmail())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(startsWith("Tu cuenta ya está verificada")));
    }

    // ---- helpers ----

    private ResultActions resend(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email/resend").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email)));
    }

    private ResultActions changePendingEmail(String email, String password, String newEmail) throws Exception {
        String json = """
                {"email": "%s", "password": "%s", "newEmail": "%s"}
                """.formatted(email, password, newEmail);
        return mockMvc.perform(post("/api/v1/auth/verify-email/change-email")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
