package com.portfolix.api.common.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;

/**
 * Manda los mails de la app en segundo plano.
 * <p>
 * {@link #send} no manda nada en el momento: publica el mail como evento, y {@link #deliver} lo recibe
 * recién cuando se confirma (commit) la transacción de quien lo pidió, en otro hilo. Así:
 * <ul>
 *   <li>el request no espera al servidor de mails, que puede tardar segundos;</li>
 *   <li>si el envío falla, la operación (ej.: el registro) igual se completa, y el mail se puede reenviar;</li>
 *   <li>si la transacción se deshace (rollback), el mail no sale: nunca llega un link a un token que no existe;</li>
 *   <li>los flujos que no deben revelar si un mail tiene cuenta tardan lo mismo en los dos casos.</li>
 * </ul>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final Locale LOCALE = Locale.forLanguageTag("es-AR");

    private final ApplicationEventPublisher events;
    private final MailTransport transport;
    private final ITemplateEngine templateEngine;

    public MailService(ApplicationEventPublisher events, MailTransport transport, ITemplateEngine templateEngine) {
        this.events = events;
        this.transport = transport;
        this.templateEngine = templateEngine;
    }

    /** Encola el mail: sale cuando se confirma la transacción actual (o enseguida, si no hay una). */
    public void send(OutgoingMail mail) {
        events.publishEvent(mail);
    }

    /**
     * Arma el mail con su plantilla y lo manda por {@link MailTransport} (SMTP o la API de Brevo, según
     * {@code portfolix.mail.provider}). No se llama directamente: Spring la invoca después del commit
     * ({@code @TransactionalEventListener}) en un hilo de {@code mailExecutor} ({@code @Async}).
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
            transport.send(mail.to(), mail.subject(), html);
        } catch (RuntimeException ex) {
            log.error("No se pudo mandar el mail \"{}\" a {}", mail.subject(), mail.to(), ex);
        }
    }
}
