package com.enterprise.auth.platform.modules.user.api;

/** Password hashing contract needed by user write use cases. */
public interface UserPasswordHashPort {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
