package com.portfolix.api.user.preference.dto;

import com.portfolix.api.common.Currency;
import com.portfolix.api.user.preference.DecimalSeparator;
import com.portfolix.api.user.preference.Theme;

/**
 * PATCH: solo cambia lo que viene. Un campo ausente (o {@code null}) queda como estaba, por eso
 * {@code hideAmounts} es {@code Boolean}: con {@code boolean}, no se distinguiría "no vino" de "false"
 * (y además Jackson 3 rechazaría el JSON si falta).
 */
public record PreferencesUpdateRequest(
        Theme theme,
        DecimalSeparator decimalSeparator,
        Currency currency,
        Boolean hideAmounts
) {
}
