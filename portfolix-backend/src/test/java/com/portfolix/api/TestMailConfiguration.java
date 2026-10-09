package com.portfolix.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Los tests no mandan mails de verdad: el {@link TestMailbox} los guarda. Como es un
 * {@code JavaMailSender}, Spring Boot ya no crea el suyo (el que se conecta al SMTP).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {

    @Bean
    TestMailbox testMailbox() {
        return new TestMailbox();
    }
}
