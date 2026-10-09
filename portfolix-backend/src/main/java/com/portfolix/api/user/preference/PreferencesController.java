package com.portfolix.api.user.preference;

import com.portfolix.api.security.CurrentUserId;
import com.portfolix.api.user.preference.dto.PreferencesResponse;
import com.portfolix.api.user.preference.dto.PreferencesUpdateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/preferences")
public class PreferencesController {

    private final PreferenceService preferenceService;

    public PreferencesController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    /** Si el usuario nunca guardó nada, devuelve los valores por defecto. */
    @GetMapping
    public PreferencesResponse get(@CurrentUserId Long userId) {
        return preferenceService.get(userId);
    }

    /** Cambia solo los campos que vienen y devuelve todas las preferencias. */
    @PatchMapping
    public PreferencesResponse update(@CurrentUserId Long userId, @RequestBody PreferencesUpdateRequest request) {
        return preferenceService.update(userId, request);
    }
}
