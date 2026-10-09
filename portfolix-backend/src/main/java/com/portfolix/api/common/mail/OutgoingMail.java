package com.portfolix.api.common.mail;

import java.util.Map;

/**
 * Un mail listo para mandar. Las variables tienen que ser datos simples (textos, números):
 * el mail se arma en otro hilo, cuando la transacción ya terminó, y ahí una entidad JPA
 * ya no puede cargar sus relaciones.
 *
 * @param template  plantilla Thymeleaf, relativa a {@code templates/} y sin {@code .html} (ej.: {@code mail/verify-email})
 * @param variables valores que usa la plantilla
 */
public record OutgoingMail(String to, String subject, String template, Map<String, Object> variables) {

    public OutgoingMail {
        variables = Map.copyOf(variables);
    }
}
