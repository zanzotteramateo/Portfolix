package com.portfolix.api.user.preference;

import org.springframework.data.jpa.repository.JpaRepository;

/** La clave es el id del usuario. */
public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {
}
