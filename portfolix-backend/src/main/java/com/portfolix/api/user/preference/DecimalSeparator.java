package com.portfolix.api.user.preference;

/** Cómo ve los números el usuario. El front los formatea; el backend lo usa para exportar el CSV. */
public enum DecimalSeparator {
    /** 1.234,56 (el uso en Argentina). */
    COMMA,
    /** 1,234.56. */
    PERIOD
}
