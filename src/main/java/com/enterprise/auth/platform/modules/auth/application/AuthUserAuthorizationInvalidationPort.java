package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.user.api.UserAuthorizationInvalidationPort;
import org.springframework.stereotype.Component;

/** Adapts auth authorization-cache invalidation to the user module API. */
@Component
public final class AuthUserAuthorizationInvalidationPort implements UserAuthorizationInvalidationPort {

    private final AuthPermissionSnapshotInvalidationService invalidationService;

    public AuthUserAuthorizationInvalidationPort(AuthPermissionSnapshotInvalidationService invalidationService) {
        this.invalidationService = invalidationService;
    }

    @Override
    public void invalidateUser(Long userId, String tenantId, String username) {
        invalidationService.invalidateUser(userId, tenantId, username);
    }
}
