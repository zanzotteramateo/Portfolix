package com.portfolix.api.auth;

import com.portfolix.api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Bloqueo por intentos fallidos contra la app real (5 intentos, 5 minutos). Que el bloqueo se levante
 * solo con el tiempo se prueba en LoginLockoutTest, con un reloj que se puede adelantar.
 */
class LoginLockoutIntegrationTest extends ApiIntegrationTest {

    private static final String WRONG_PASSWORD = "Incorrecta1!";
    private static final String BAD_CREDENTIALS = "Correo o contraseña incorrectos";

    @Test
    void fiveWrongPasswords_lockTheLogin_evenWithTheRightPassword() throws Exception {
        String email = registerVerifiedUser();

        expectWarnings(email);
        login(email, WRONG_PASSWORD, false)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.message").value(startsWith("Por seguridad, bloqueamos el acceso")));

        login(email, PASSWORD, false).andExpect(status().isTooManyRequests());
    }

    @Test
    void anEmailWithoutAccount_getsTheSameWarningsAndLock() throws Exception {
        String unknown = uniqueEmail();

        expectWarnings(unknown);
        login(unknown, WRONG_PASSWORD, false).andExpect(status().isTooManyRequests());
    }

    @Test
    void theRightPassword_startsTheCountAgain() throws Exception {
        String email = registerVerifiedUser();
        for (int i = 0; i < 4; i++) {
            login(email, WRONG_PASSWORD, false).andExpect(status().isUnauthorized());
        }

        login(email, PASSWORD, false).andExpect(status().isOk());

        login(email, WRONG_PASSWORD, false).andExpect(jsonPath("$.message").value(BAD_CREDENTIALS));
    }

    @Test
    void resettingThePassword_unlocksTheLogin() throws Exception {
        String email = registerVerifiedUser();
        for (int i = 0; i < 5; i++) {
            login(email, WRONG_PASSWORD, false);
        }
        login(email, PASSWORD, false).andExpect(status().isTooManyRequests());

        mockMvc.perform(post("/api/v1/auth/password/forgot").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\"}".formatted(email))).andExpect(status().isAccepted());
        String token = mailbox.awaitMail(email, 2).token();
        mockMvc.perform(post("/api/v1/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"%s\", \"newPassword\": \"NuevaClave2026!\"}".formatted(token)))
                .andExpect(status().isNoContent());

        login(email, "NuevaClave2026!", false).andExpect(status().isOk());
    }

    @Test
    void wrongPasswordsInChangePendingEmail_countForTheSameLock() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            changePendingEmail(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        changePendingEmail(email, WRONG_PASSWORD).andExpect(status().isTooManyRequests());

        // El bloqueo es del mail, no del endpoint: el login también queda bloqueado.
        login(email, PASSWORD, false).andExpect(status().isTooManyRequests());
    }

    /** Los 4 primeros errores: sin aviso en los dos primeros, y con los intentos que quedan en los otros dos. */
    private void expectWarnings(String email) throws Exception {
        String lockNotice = " antes de que bloqueemos el acceso por 5 minutos";
        login(email, WRONG_PASSWORD, false).andExpect(jsonPath("$.message").value(BAD_CREDENTIALS));
        login(email, WRONG_PASSWORD, false).andExpect(jsonPath("$.message").value(BAD_CREDENTIALS));
        login(email, WRONG_PASSWORD, false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(BAD_CREDENTIALS + ". Te quedan 2 intentos" + lockNotice));
        login(email, WRONG_PASSWORD, false)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(BAD_CREDENTIALS + ". Te queda 1 intento" + lockNotice));
    }

    private ResultActions changePendingEmail(String email, String password) throws Exception {
        String json = """
                {"email": "%s", "password": "%s", "newEmail": "%s"}
                """.formatted(email, password, uniqueEmail());
        return mockMvc.perform(post("/api/v1/auth/verify-email/change-email")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
