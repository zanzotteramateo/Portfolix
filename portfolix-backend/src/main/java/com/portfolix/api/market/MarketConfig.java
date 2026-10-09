package com.portfolix.api.market;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@EnableConfigurationProperties(MarketProperties.class)
public class MarketConfig {

    /**
     * Hilos para refrescar los cachés de mercado en segundo plano. Son hilos virtuales (Java 21+):
     * pasan casi todo el tiempo esperando respuestas de red, y un hilo virtual espera sin ocupar
     * un hilo del sistema operativo. El ForkJoinPool común (el default de Caffeine) es para cálculo.
     */
    @Bean(destroyMethod = "close")
    ExecutorService marketExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
