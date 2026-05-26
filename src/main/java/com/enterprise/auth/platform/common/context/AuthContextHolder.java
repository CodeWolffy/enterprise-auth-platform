package com.enterprise.auth.platform.common.context;

import com.enterprise.auth.platform.dto.model.SessionPrincipal;
import com.enterprise.auth.platform.dto.model.UserAccount;
import java.util.Optional;

public final class AuthContextHolder {

    private static final ThreadLocal<UserAccount> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<SessionPrincipal> CURRENT_SESSION = new ThreadLocal<>();

    private AuthContextHolder() {
    }

    public static void set(UserAccount user, SessionPrincipal session) {
        CURRENT_USER.set(user);
        CURRENT_SESSION.set(session);
    }

    public static Optional<UserAccount> currentUser() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static Optional<SessionPrincipal> currentSession() {
        return Optional.ofNullable(CURRENT_SESSION.get());
    }

    public static void clear() {
        CURRENT_USER.remove();
        CURRENT_SESSION.remove();
    }
}