package com.portfolix.api.user.preference;

import com.portfolix.api.common.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Preferencias de un usuario. La clave es el id del usuario (una fila por usuario, borrada en cascada
 * con él). La fila existe recién cuando el usuario guarda algo; mientras tanto valen {@link #defaultsFor}.
 */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    @Setter(AccessLevel.NONE)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Theme theme;

    @Enumerated(EnumType.STRING)
    @Column(name = "decimal_separator", nullable = false, length = 10)
    private DecimalSeparator decimalSeparator;

    /** Moneda en la que el dashboard muestra los montos. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    /** El "ojo" de la cabecera: oculta los montos en pantalla. */
    @Column(name = "hide_amounts", nullable = false)
    private boolean hideAmounts;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private UserPreferences(Long userId, Theme theme, DecimalSeparator decimalSeparator, Currency currency,
                            boolean hideAmounts) {
        this.userId = userId;
        this.theme = theme;
        this.decimalSeparator = decimalSeparator;
        this.currency = currency;
        this.hideAmounts = hideAmounts;
    }

    /** Las preferencias de alguien que nunca cambió nada: tema claro, coma decimal, pesos, montos visibles. */
    public static UserPreferences defaultsFor(Long userId) {
        return new UserPreferences(userId, Theme.LIGHT, DecimalSeparator.COMMA, Currency.ARS, false);
    }
}
