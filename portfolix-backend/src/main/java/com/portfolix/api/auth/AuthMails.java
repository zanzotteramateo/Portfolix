package com.portfolix.api.auth;

import com.portfolix.api.auth.token.EmailTokenProperties;
import com.portfolix.api.common.DurationText;
import com.portfolix.api.common.mail.MailProperties;
import com.portfolix.api.common.mail.MailService;
import com.portfolix.api.common.mail.OutgoingMail;
import com.portfolix.api.user.User;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Arma los mails de autenticación (asunto, plantilla y link) y se los pasa a {@link MailService}.
 * <p>
 * Los links abren una página del front (no la API directamente), y es esa página la que manda
 * el token a la API con un POST. Los antivirus y las vistas previas de los clientes de mail abren
 * los links por su cuenta: si el link fuera directo a la API, gastarían el token antes que el usuario.
 */
@Component
public class AuthMails {

    private final MailService mailService;
    private final MailProperties mailProperties;
    private final EmailTokenProperties tokenProperties;

    public AuthMails(MailService mailService, MailProperties mailProperties, EmailTokenProperties tokenProperties) {
        this.mailService = mailService;
        this.mailProperties = mailProperties;
        this.tokenProperties = tokenProperties;
    }

    public void sendEmailVerification(User user, String token) {
        mailService.send(new OutgoingMail(user.getEmail(), "Confirmá tu correo en Portfolix", "mail/verify-email",
                Map.of("name", user.getFullName(),
                        "link", frontendLink("/verify-email", token),
                        "validity", DurationText.of(tokenProperties.verificationTtl()))));
    }

    public void sendPasswordReset(User user, String token) {
        mailService.send(new OutgoingMail(user.getEmail(), "Restablecé tu contraseña de Portfolix", "mail/reset-password",
                Map.of("name", user.getFullName(),
                        "link", frontendLink("/reset-password", token),
                        "validity", DurationText.of(tokenProperties.passwordResetTtl()))));
    }

    /** El link va al mail nuevo: confirmarlo prueba que esa dirección es del usuario. */
    public void sendEmailChange(User user, String newEmail, String token) {
        mailService.send(new OutgoingMail(newEmail, "Confirmá tu nuevo correo en Portfolix", "mail/confirm-email-change",
                Map.of("name", user.getFullName(),
                        "link", frontendLink("/confirm-email-change", token),
                        "validity", DurationText.of(tokenProperties.emailChangeTtl()))));
    }

    private String frontendLink(String path, String token) {
        return UriComponentsBuilder.fromUriString(mailProperties.frontendUrl())
                .path(path)
                .queryParam("token", token)
                .toUriString();
    }
}
