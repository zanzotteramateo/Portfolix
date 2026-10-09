package com.portfolix.api.user.dto;

import com.portfolix.api.user.User;

/**
 * @param hasPassword si la cuenta tiene contraseña. Las que entran solo con Google no tienen, y para cambiar
 *                    el mail o eliminar la cuenta primero tienen que crearse una ("¿Olvidaste tu contraseña?")
 */
public record UserResponse(Long id, String fullName, String email, boolean hasPassword) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPasswordHash() != null);
    }
}
