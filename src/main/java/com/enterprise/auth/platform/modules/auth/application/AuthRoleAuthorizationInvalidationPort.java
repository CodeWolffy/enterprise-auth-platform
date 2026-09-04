package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.role.api.RoleAuthorizationInvalidationPort;
import org.springframework.stereotype.Component;

/** Adapts auth snapshot invalidation to the role-owned contract. */
@Component
public final class AuthRoleAuthorizationInvalidationPort implements RoleAuthorizationInvalidationPort {

    private final AuthPermissionSnapshotInvalidationService invalidationService;

    public AuthRoleAuthorizationInvalidationPort(AuthPermissionSnapshotInvalidationService invalidationService) {
        this.invalidationService = invalidationService;
    }

    @Override
    public void invalidateUser(Long userId, String tenantId, String username) {
        invalidationService.invalidateUser(userId, tenantId, username);
    }
}
