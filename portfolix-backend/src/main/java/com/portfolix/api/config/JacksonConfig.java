package com.portfolix.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration
public class JacksonConfig {

    /**
     * Todos los BigDecimal (cantidades, precios, montos) salen como string: "0.05" en vez de 0.05.
     * JavaScript guarda los números como double y perdería precisión con 18 decimales o montos grandes.
     * Se quitan los ceros de relleno de la base (NUMERIC(38,18)): 0.100000000000000000 → "0.1".
     * En la entrada se aceptan tanto "0.05" como 0.05.
     */
    @Bean
    SimpleModule decimalAsStringModule() {
        SimpleModule module = new SimpleModule("decimal-as-string");
        module.addSerializer(BigDecimal.class, new ValueSerializer<>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator generator, SerializationContext context) {
                generator.writeString(value.stripTrailingZeros().toPlainString());
            }
        });
        return module;
    }
}
