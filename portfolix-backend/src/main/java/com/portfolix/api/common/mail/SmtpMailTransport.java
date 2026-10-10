package com.portfolix.api.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Manda los mails por SMTP ({@code spring.mail.*}: Mailpit en dev, el proveedor que corresponda en
 * producción). Es la opción por defecto: no hace falta configurar nada nuevo para seguir como está.
 */
@Component
@ConditionalOnProperty(name = "portfolix.mail.provider", havingValue = "smtp", matchIfMissing = true)
class SmtpMailTransport implements MailTransport {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    SmtpMailTransport(JavaMailSender mailSender, MailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException ex) {
            throw new IllegalStateException("No se pudo armar el mail por SMTP", ex);
        }
    }
}
