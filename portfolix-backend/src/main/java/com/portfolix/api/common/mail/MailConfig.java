package com.portfolix.api.common.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * {@code @EnableAsync} activa {@code @Async}: el método anotado corre en el executor que indica,
 * y quien lo llama sigue sin esperarlo.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /**
     * Hilos para mandar mails. Son virtuales, como los de {@code marketExecutor}: casi todo el tiempo
     * esperan la respuesta del servidor SMTP. Al apagar la app, {@code close} espera a que terminen
     * los mails que se están mandando.
     */
    @Bean(destroyMethod = "close")
    ExecutorService mailExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
