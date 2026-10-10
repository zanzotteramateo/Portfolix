package com.portfolix.api.common.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manda los mails por la API HTTP de Brevo (api.brevo.com), no por SMTP: algunos hostings gratis
 * (ej.: Render) bloquean los puertos SMTP, así que ahí el {@link SmtpMailTransport} no sirve.
 * Se activa con {@code portfolix.mail.provider=brevo-api} ({@code MAIL_PROVIDER}).
 */
@Component
@ConditionalOnProperty(name = "portfolix.mail.provider", havingValue = "brevo-api")
class BrevoMailTransport implements MailTransport {

    /** El remitente ({@code MailProperties.from}) viene como "Nombre <mail@dominio.com>"; la API de Brevo
     *  pide el nombre y el mail por separado. */
    private static final Pattern FROM_PATTERN = Pattern.compile("^(.*)<(.+)>$");

    private final RestClient restClient;
    private final String senderName;
    private final String senderEmail;

    BrevoMailTransport(RestClient.Builder builder, MailProperties properties) {
        if (properties.brevoApiKey() == null || properties.brevoApiKey().isBlank()) {
            throw new IllegalStateException("Falta la clave de la API de Brevo (variable de entorno BREVO_API_KEY)");
        }
        Matcher matcher = FROM_PATTERN.matcher(properties.from().trim());
        if (!matcher.matches()) {
            throw new IllegalStateException(
                    "El remitente (MAIL_FROM) tiene que tener el formato \"Nombre <mail@dominio.com>\"");
        }
        this.senderName = matcher.group(1).trim();
        this.senderEmail = matcher.group(2).trim();
        this.restClient = builder
                .baseUrl("https://api.brevo.com/v3")
                .defaultHeader("api-key", properties.brevoApiKey())
                .build();
    }

    @Override
    public void send(String to, String subject, String html) {
        restClient.post()
                .uri("/smtp/email")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "sender", Map.of("name", senderName, "email", senderEmail),
                        "to", List.of(Map.of("email", to)),
                        "subject", subject,
                        "htmlContent", html))
                .retrieve()
                .toBodilessEntity();
    }
}
