package com.portfolix.api;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Reemplaza al {@code JavaMailSender} en los tests de integración: en vez de mandar los mails,
 * los guarda para que el test los lea (ej.: para sacar el token del link, como haría el usuario).
 * <p>
 * La app manda los mails en otro hilo después del commit, así que pueden llegar un instante después
 * de la respuesta HTTP: los métodos de lectura esperan a que lleguen.
 */
public class TestMailbox extends JavaMailSenderImpl {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    /** Cuánto se espera para confirmar que NO llega un mail. */
    private static final Duration QUIET_PERIOD = Duration.ofMillis(300);

    private final List<ReceivedMail> mails = new CopyOnWriteArrayList<>();

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            mails.add(ReceivedMail.from(message));
        }
    }

    public List<ReceivedMail> mailsTo(String email) {
        return mails.stream().filter(mail -> mail.to().equals(email)).toList();
    }

    /** Espera el primer mail a esa dirección. */
    public ReceivedMail awaitMail(String email) {
        return awaitMail(email, 1);
    }

    /** Espera a que esa dirección tenga {@code count} mails y devuelve el último. */
    public ReceivedMail awaitMail(String email, int count) {
        await().atMost(TIMEOUT).pollInterval(Duration.ofMillis(10))
                .until(() -> mailsTo(email).size() >= count);
        List<ReceivedMail> received = mailsTo(email);
        assertThat(received).as("mails a %s", email).hasSize(count);
        return received.getLast();
    }

    /** Confirma que a esa dirección no le llega ningún mail más: sigue teniendo {@code count}. */
    public void assertNoNewMail(String email, int count) {
        await().during(QUIET_PERIOD).atMost(QUIET_PERIOD.multipliedBy(3))
                .until(() -> mailsTo(email).size() == count);
    }

    /** Un mail recibido, con el HTML ya armado por la plantilla. */
    public record ReceivedMail(String to, String subject, String html) {

        private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");

        static ReceivedMail from(MimeMessage message) {
            try {
                InternetAddress to = (InternetAddress) message.getRecipients(Message.RecipientType.TO)[0];
                return new ReceivedMail(to.getAddress(), message.getSubject(), (String) message.getContent());
            } catch (MessagingException | IOException ex) {
                throw new IllegalStateException("No se pudo leer el mail", ex);
            }
        }

        /** El token del link del mail. */
        public String token() {
            Matcher matcher = TOKEN.matcher(html);
            assertThat(matcher.find()).as("el mail tiene un link con token").isTrue();
            return matcher.group(1);
        }
    }
}
