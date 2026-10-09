package com.portfolix.api.common.mail;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Manda los mails de la app en segundo plano.
 * <p>
 * {@link #send} no manda nada en el momento: publica el mail como evento, y {@link #deliver} lo recibe
 * recién cuando se confirma (commit) la transacción de quien lo pidió, en otro hilo. Así:
 * <ul>
 *   <li>el request no espera al servidor SMTP, que puede tardar segundos;</li>
 *   <li>si el SMTP falla, la operación (ej.: el registro) igual se completa, y el mail se puede reenviar;</li>
 *   <li>si la transacción se deshace (rollback), el mail no sale: nunca llega un link a un token que no existe;</li>
 *   <li>los flujos que no deben revelar si un mail tiene cuenta tardan lo mismo en los dos casos.</li>
 * </ul>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final Locale LOCALE = Locale.forLanguageTag("es-AR");

    private final ApplicationEventPublisher events;
    private final JavaMailSender mailSender;
    private final ITemplateEngine templateEngine;
    private final MailProperties properties;

    public MailService(ApplicationEventPublisher events, JavaMailSender mailSender,
                       ITemplateEngine templateEngine, MailProperties properties) {
        this.events = events;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
    }

    /** Encola el mail: sale cuando se confirma la transacción actual (o enseguida, si no hay una). */
    public void send(OutgoingMail mail) {
        events.publishEvent(mail);
    }

    /**
     * Arma el mail con su plantilla y lo manda. No se llama directamente: Spring la invoca después
     * del commit ({@code @TransactionalEventListener}) en un hilo de {@code mailExecutor} ({@code @Async}).
     * {@code fallbackExecution}: sin esto, un mail pedido fuera de una transacción se descartaría sin aviso.
     * <p>
     * Como corre en otro hilo, un error no le llega a quien pidió el mail: se registra en el log.
     */
    @Async("mailExecutor")
    @TransactionalEventListener(fallbackExecution = true)
    public void deliver(OutgoingMail mail) {
        try {
            Context context = new Context(LOCALE, mail.variables());
            context.setVariable("subject", mail.subject());
            String html = templateEngine.process(mail.template(), context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | RuntimeException ex) {
            log.error("No se pudo mandar el mail \"{}\" a {}", mail.subject(), mail.to(), ex);
        }
    }
}
