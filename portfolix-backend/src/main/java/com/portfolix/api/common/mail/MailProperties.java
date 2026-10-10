package com.portfolix.api.common.mail;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración propia de los mails. Se lee de {@code portfolix.mail}.
 *
 * @param from         remitente, ej.: {@code Portfolix <no-reply@tudominio.com>}
 * @param frontendUrl  URL del front: los links de los mails abren sus páginas
 * @param provider     smtp ({@code spring.mail.*}, como Mailpit en dev) o brevo-api (la API HTTP de
 *                     Brevo, para hostings gratis que bloquean los puertos SMTP, ej.: Render)
 * @param brevoApiKey  clave de la API de Brevo; solo hace falta con {@code provider=brevo-api}
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.mail")
public record MailProperties(
        @NotBlank(message = "Falta el remitente de los mails (variable de entorno MAIL_FROM)") String from,
        @NotBlank(message = "Falta la URL del front para los links de los mails (variable de entorno FRONTEND_URL)")
        String frontendUrl,
        @NotNull Provider provider,
        String brevoApiKey
) {

    public enum Provider {
        SMTP,
        BREVO_API
    }
}
