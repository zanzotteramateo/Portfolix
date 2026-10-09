package com.portfolix.api.user.preference;

import com.portfolix.api.user.preference.dto.PreferencesResponse;
import com.portfolix.api.user.preference.dto.PreferencesUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Preferencias de la app. El backend solo las guarda y las devuelve: el front las aplica (ej.: sigue
 * mandando {@code currency} en cada pedido del dashboard). La excepción es el separador decimal,
 * que se usa para exportar el CSV.
 */
@Service
public class PreferenceService {

    private final UserPreferencesRepository repository;

    public PreferenceService(UserPreferencesRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PreferencesResponse get(Long userId) {
        return PreferencesResponse.from(current(userId));
    }

    /** Cambia solo lo que viene en el pedido. La fila se crea la primera vez que el usuario guarda algo. */
    @Transactional
    public PreferencesResponse update(Long userId, PreferencesUpdateRequest changes) {
        UserPreferences preferences = current(userId);
        if (changes.theme() != null) {
            preferences.setTheme(changes.theme());
        }
        if (changes.decimalSeparator() != null) {
            preferences.setDecimalSeparator(changes.decimalSeparator());
        }
        if (changes.currency() != null) {
            preferences.setCurrency(changes.currency());
        }
        if (changes.hideAmounts() != null) {
            preferences.setHideAmounts(changes.hideAmounts());
        }
        return PreferencesResponse.from(repository.save(preferences));
    }

    @Transactional(readOnly = true)
    public DecimalSeparator decimalSeparator(Long userId) {
        return current(userId).getDecimalSeparator();
    }

    private UserPreferences current(Long userId) {
        return repository.findById(userId).orElseGet(() -> UserPreferences.defaultsFor(userId));
    }
}
