package com.nandini.knowledgeassistant.user;

import com.nandini.knowledgeassistant.common.ApiException;
import com.nandini.knowledgeassistant.common.ErrorCode;
import com.nandini.knowledgeassistant.common.audit.AuditAction;
import com.nandini.knowledgeassistant.common.audit.AuditService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    /**
     * Hash of a random value. Checking unknown usernames against it keeps the response time
     * similar for "no such user" and "wrong password", which prevents username enumeration
     * through timing.
     */
    private final String dummyHash;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(String username, String email, String rawPassword, Role role) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(normalizedUsername)
                || (normalizedEmail != null && users.existsByEmailIgnoreCase(normalizedEmail))) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN, "Username or email is already registered.");
        }
        try {
            User user = users.saveAndFlush(
                    new User(normalizedUsername, normalizedEmail, passwordEncoder.encode(rawPassword), role));
            audit.recordFor(user.getId(), AuditAction.USER_REGISTERED, "USER", user.getId(), "role=" + role);
            return user;
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent registrations raced past the exists-check; the unique index decides.
            throw new ApiException(ErrorCode.USERNAME_TAKEN, "Username or email is already registered.", ex);
        }
    }

    @Transactional(readOnly = true)
    public User authenticate(String username, String rawPassword) {
        Optional<User> user = users.findByUsernameIgnoreCase(username.trim());
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(rawPassword, hash);
        if (user.isEmpty() || !matches) {
            audit.recordFor(user.map(User::getId).orElse(null), AuditAction.LOGIN_FAILED, "USER", null, null);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid username or password.");
        }
        audit.recordFor(user.get().getId(), AuditAction.LOGIN_SUCCEEDED, "USER", user.get().getId(), null);
        return user.get();
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return users.findByUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, "User not found."));
    }
}
