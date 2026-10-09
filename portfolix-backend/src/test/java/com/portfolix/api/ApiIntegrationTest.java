package com.portfolix.api;

import com.jayway.jsonpath.JsonPath;
import com.portfolix.api.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración de la API: levanta la app completa contra un Postgres
 * de Testcontainers y ofrece helpers para crear datos a través de los endpoints reales.
 * <p>
 * La autenticación se simula con {@code jwt()} (un token válido con el id del usuario en {@code sub});
 * el login real se prueba en AuthFlowIntegrationTest. Cada test crea sus propios usuarios
 * con mail único, así no se pisan aunque compartan la base. Los mails no se mandan:
 * quedan en {@link #mailbox}. Los ID tokens de Google se firman con {@link TestGoogle}.
 */
@SpringBootTest // precios fijos: ver src/test/resources/config/application.properties
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestMailConfiguration.class, TestGoogleConfiguration.class})
public abstract class ApiIntegrationTest {

    protected static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    protected static final String PASSWORD = "Inversion2026!";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserService userService;
    @Autowired
    protected TestMailbox mailbox;

    /** Crea un usuario con mail único y devuelve su id. La cuenta queda sin verificar y no recibe mails. */
    protected Long createUser(String fullName) {
        return userService.createUser(fullName, uniqueEmail(), PASSWORD).getId();
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@email.com";
    }

    protected static RequestPostProcessor authenticatedAs(Long userId) {
        return jwt().jwt(token -> token.subject(userId.toString()));
    }

    // ---- Auth a través de los endpoints reales ----

    /** {@code POST /auth/register} con los términos aceptados. La cuenta queda sin verificar. */
    protected ResultActions register(String fullName, String email, String password) throws Exception {
        String json = """
                {"fullName": "%s", "email": "%s", "password": "%s", "acceptedTerms": true}
                """.formatted(fullName, email, password);
        return mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** {@code POST /auth/verify-email}, como la página del front que abre el link del mail. */
    protected ResultActions verifyEmail(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/verify-email").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token)));
    }

    /** Registra una cuenta y la verifica con el link del mail, como un usuario real. Devuelve el mail. */
    protected String registerVerifiedUser() throws Exception {
        String email = uniqueEmail();
        register("Juan Pérez", email, PASSWORD).andExpect(status().isCreated());
        verifyEmail(mailbox.awaitMail(email).token()).andExpect(status().isNoContent());
        return email;
    }

    protected ResultActions login(String email, String password, boolean rememberMe) throws Exception {
        String json = """
                {"email": "%s", "password": "%s", "rememberMe": %s}
                """.formatted(email, password, rememberMe);
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    // ---- Portafolios y transacciones ----

    /** Crea un portafolio y devuelve su id. Falla si no responde 201. */
    protected long createPortfolio(RequestPostProcessor user, String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/portfolios").with(user)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /**
     * {@code POST /transactions}. Cantidad, precio y notas se escriben tal cual en el JSON,
     * así se puede probar con string ({@code "\"0.05\""}), número ({@code "0.05"}) o {@code "null"}.
     */
    protected ResultActions registerTransaction(RequestPostProcessor user, long portfolioId, String symbol,
                                                String type, String quantity, String price, LocalDate date,
                                                String notes) throws Exception {
        String json = """
                {"portfolioId": %d, "assetSymbol": "%s", "type": "%s", "quantity": %s,
                 "price": %s, "tradeDate": "%s", "notes": %s}
                """.formatted(portfolioId, symbol, type, quantity, price, date, notes);
        return mockMvc.perform(post("/api/v1/transactions").with(user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Registra una transacción que tiene que ser válida (falla si no responde 201) y devuelve su id. */
    protected long recordTransaction(RequestPostProcessor user, long portfolioId, String symbol, String type,
                                     String quantity, String price, LocalDate date) throws Exception {
        String body = registerTransaction(user, portfolioId, symbol, type, "\"" + quantity + "\"",
                "\"" + price + "\"", date, "null")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Fechas relativas a hoy en Argentina, como las valida la API. */
    protected static LocalDate daysAgo(int days) {
        return LocalDate.now(ARGENTINA).minusDays(days);
    }

    protected static LocalDate yesterday() {
        return daysAgo(1);
    }
}
