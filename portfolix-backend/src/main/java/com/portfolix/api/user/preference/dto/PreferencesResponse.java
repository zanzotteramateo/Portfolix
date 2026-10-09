package com.portfolix.api.user.preference.dto;

import com.portfolix.api.common.Currency;
import com.portfolix.api.user.preference.DecimalSeparator;
import com.portfolix.api.user.preference.Theme;
import com.portfolix.api.user.preference.UserPreferences;

public record PreferencesResponse(Theme theme, DecimalSeparator decimalSeparator, Currency currency,
                                  boolean hideAmounts) {

    public static PreferencesResponse from(UserPreferences preferences) {
        return new PreferencesResponse(preferences.getTheme(), preferences.getDecimalSeparator(),
                preferences.getCurrency(), preferences.isHideAmounts());
    }
}
