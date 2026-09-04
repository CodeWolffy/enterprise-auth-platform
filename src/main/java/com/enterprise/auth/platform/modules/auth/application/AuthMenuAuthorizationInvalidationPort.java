package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.menu.api.MenuAuthorizationInvalidationPort;
import org.springframework.stereotype.Component;

/** Adapts auth snapshot invalidation to the menu-owned contract. */
@Component
public final class AuthMenuAuthorizationInvalidationPort implements MenuAuthorizationInvalidationPort {

    private final AuthPermissionSnapshotInvalidationService invalidationService;

    public AuthMenuAuthorizationInvalidationPort(AuthPermissionSnapshotInvalidationService invalidationService) {
        this.invalidationService = invalidationService;
    }

    @Override
    public void invalidateAll() {
        invalidationService.invalidateAll();
    }
}
