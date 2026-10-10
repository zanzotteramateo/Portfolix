package com.portfolix.api.common.mail;

/**
 * Manda un mail ya armado (HTML). {@link MailService} depende de esta interfaz, no de una implementación
 * concreta: {@link SmtpMailTransport} o {@link BrevoMailTransport}, según {@code portfolix.mail.provider}.
 */
interface MailTransport {

    void send(String to, String subject, String html);
}
