package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.user.api.UserPasswordHashPort;
import org.springframework.stereotype.Component;

/** Adapts the auth password hasher to user write use cases. */
@Component
public final class AuthUserPasswordHashPort implements UserPasswordHashPort {

    private final PasswordHasher passwordHasher;

    public AuthUserPasswordHashPort(PasswordHasher passwordHasher) {
        this.passwordHasher = passwordHasher;
    }

    @Override
    public String hash(String rawPassword) {
        return passwordHasher.hash(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordHasher.matches(rawPassword, encodedPassword);
    }
}
