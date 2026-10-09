package com.portfolix.api.user;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.user.dto.UserResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
public class UserService {

    static final String EMAIL_TAKEN_MESSAGE = "Ya existe una cuenta con este correo";
    static final String SAME_PASSWORD_MESSAGE = "La contraseña nueva tiene que ser distinta de la actual";
    private static final String EMAIL_FIELD = "email";
    private static final String NEW_EMAIL_FIELD = "newEmail";
    private static final String NEW_PASSWORD_FIELD = "newPassword";
    private static final int MAX_NAME_LENGTH = 100;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Crea un usuario con la contraseña hasheada. Quien llama ya verificó que aceptó los términos.
     * La cuenta nace sin verificar: se activa con el link que se manda por mail.
     */
    @Transactional
    public User createUser(String fullName, String email, String rawPassword) {
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            // Asociado al campo del formulario de registro: el front lo muestra debajo del mail.
            throw new BusinessException(EMAIL_FIELD, EMAIL_TAKEN_MESSAGE);
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), fullName.trim(), clock.instant());
        return save(user);
    }

    /**
     * Crea un usuario que entra con un proveedor externo (Google): verificado, porque el proveedor ya
     * certificó el mail, y sin contraseña. Los términos se dan por aceptados al continuar con el proveedor
     * (el front muestra el aviso junto al botón). El nombre se recorta al máximo de la columna.
     */
    @Transactional
    public User createExternalUser(String fullName, String email) {
        String normalizedEmail = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new BusinessException(EMAIL_TAKEN_MESSAGE);
        }
        String name = fullName.strip();
        User user = new User(normalizedEmail, null, name.substring(0, Math.min(name.length(), MAX_NAME_LENGTH)),
                clock.instant());
        user.setEmailVerified(true);
        return save(user);
    }

    @Transactional
    public void markEmailVerified(Long userId) {
        getById(userId).setEmailVerified(true);
    }

    /** Le saca la contraseña: desde ahí entra solo con su proveedor externo (o se crea una con el reset). */
    @Transactional
    public void removePassword(Long userId) {
        getById(userId).setPasswordHash(null);
    }

    /**
     * Cambia la contraseña. La nueva no puede ser igual a la actual (el formato ya lo validó
     * {@code @StrongPassword} en el request). Una cuenta sin contraseña (solo Google) así se crea una.
     * Cerrar las sesiones abiertas le toca a quien llama.
     */
    @Transactional
    public void changePassword(Long userId, String newRawPassword) {
        User user = getById(userId);
        if (user.getPasswordHash() != null && passwordEncoder.matches(newRawPassword, user.getPasswordHash())) {
            throw new BusinessException(NEW_PASSWORD_FIELD, SAME_PASSWORD_MESSAGE);
        }
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
    }

    /**
     * Cambia el mail del usuario. Quien llama ya decidió que el cambio corresponde
     * (ej.: corregir el mail de una cuenta que todavía no se verificó). Si el mail nuevo
     * ya tiene cuenta, el error va asociado al campo {@code newEmail} del formulario.
     */
    @Transactional
    public void changeEmail(Long userId, String newEmail) {
        User user = getById(userId);
        String normalizedEmail = User.normalizeEmail(newEmail);
        if (normalizedEmail.equals(user.getEmail())) {
            return;
        }
        checkEmailAvailable(normalizedEmail);
        user.setEmail(normalizedEmail);
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // Otra cuenta tomó ese mail al mismo tiempo: el UNIQUE de la base frena este cambio.
            throw new BusinessException(NEW_EMAIL_FIELD, EMAIL_TAKEN_MESSAGE);
        }
    }

    /** 400 en el campo {@code newEmail} si ya hay una cuenta con ese mail. */
    @Transactional(readOnly = true)
    public void checkEmailAvailable(String email) {
        if (userRepository.existsByEmail(User.normalizeEmail(email))) {
            throw new BusinessException(NEW_EMAIL_FIELD, EMAIL_TAKEN_MESSAGE);
        }
    }

    /**
     * Borra el usuario. Todo lo suyo lo borra Postgres en cascada (ON DELETE CASCADE): portafolios
     * con sus transacciones, sesiones, tokens de mail, cuentas de Google vinculadas y preferencias.
     */
    @Transactional
    public void deleteUser(Long userId) {
        userRepository.delete(getById(userId));
    }

    private User save(User user) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Dos registros simultáneos con el mismo mail: el UNIQUE de la base frena al segundo.
            throw new BusinessException(EMAIL_TAKEN_MESSAGE);
        }
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email));
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    /**
     * Referencia al usuario sin consultar la base (un proxy de JPA con solo el id).
     * Sirve para asociarlo a otra entidad, por ejemplo al crear un portafolio.
     * El id tiene que ser de un usuario existente, como el del usuario autenticado.
     */
    public User getReference(Long id) {
        return userRepository.getReferenceById(id);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return UserResponse.from(getById(userId));
    }
}
