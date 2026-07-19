package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.user.api.UserSessionIndexPort;
import org.springframework.stereotype.Component;

/** Adapts the auth-owned session index to the user module's narrow API. */
@Component
public final class AuthSessionIndexPort implements UserSessionIndexPort {

    private final SessionIndexService sessionIndexService;

    public AuthSessionIndexPort(SessionIndexService sessionIndexService) {
        this.sessionIndexService = sessionIndexService;
    }

    @Override
    public void removeUser(Long userId) {
        sessionIndexService.removeUser(userId);
    }
}
