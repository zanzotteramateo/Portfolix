package com.portfolix.api.auth.token;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de los tokens de los links de los mails. Se lee de {@code portfolix.auth.email-token}.
 *
 * @param verificationTtl  validez del link para verificar la cuenta
 * @param passwordResetTtl validez del link para restablecer la contraseña
 * @param emailChangeTtl   validez del link para confirmar un mail nuevo
 * @param resendCooldown   tiempo mínimo entre dos mails del mismo tipo a una cuenta
 */
@Validated
@ConfigurationProperties(prefix = "portfolix.auth.email-token")
public record EmailTokenProperties(
        @NotNull Duration verificationTtl,
        @NotNull Duration passwordResetTtl,
        @NotNull Duration emailChangeTtl,
        @NotNull Duration resendCooldown
) {

    public Duration ttl(EmailTokenPurpose purpose) {
        return switch (purpose) {
            case EMAIL_VERIFICATION -> verificationTtl;
            case PASSWORD_RESET -> passwordResetTtl;
            case EMAIL_CHANGE -> emailChangeTtl;
        };
    }
}
