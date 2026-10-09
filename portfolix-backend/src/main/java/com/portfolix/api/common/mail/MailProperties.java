package com.portfolix.api.common.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración propia de los mails. Se lee de {@code portfolix.mail}.
 * La conexión con el servidor SMTP es la estándar de Spring Boot ({@code spring.mail.*}).
 *
 * @param from        remitente, ej.: {@code Portfolix <no-reply@tudominio.com>}
 * @param frontendUrl URL del front: los links de los mails abren sus páginas
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.mail")
public record MailProperties(
        @NotBlank(message = "Falta el remitente de los mails (variable de entorno MAIL_FROM)") String from,
        @NotBlank(message = "Falta la URL del front para los links de los mails (variable de entorno FRONTEND_URL)")
        String frontendUrl
) {
}
