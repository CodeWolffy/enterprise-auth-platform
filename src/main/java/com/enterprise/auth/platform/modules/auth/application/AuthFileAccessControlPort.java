package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.file.api.FileAccessControlPort;
import org.springframework.stereotype.Component;

/** Adapts the auth principal to the file module's policy contract. */
@Component
public final class AuthFileAccessControlPort implements FileAccessControlPort {

    private final CurrentUserService currentUserService;
    private final PlatformAdminSupport platformAdminSupport;

    public AuthFileAccessControlPort(
            CurrentUserService currentUserService,
            PlatformAdminSupport platformAdminSupport
    ) {
        this.currentUserService = currentUserService;
        this.platformAdminSupport = platformAdminSupport;
    }

    @Override
    public FileActor requireCurrentUser() {
        UserAccount user = currentUserService.requireCurrentUser();
        return new FileActor(
                user.id(),
                user.tenantId(),
                user.permissions(),
                platformAdminSupport.isPlatformSuperAdmin(user)
        );
    }
}
