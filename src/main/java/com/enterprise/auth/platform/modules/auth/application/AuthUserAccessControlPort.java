package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.api.UserAccessControlPort;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Exposes auth-owned current-user and data-scope behavior through the user API. */
@Component
public final class AuthUserAccessControlPort implements UserAccessControlPort {

    private final CurrentUserService currentUserService;
    private final DataScopeService dataScopeService;

    public AuthUserAccessControlPort(CurrentUserService currentUserService, DataScopeService dataScopeService) {
        this.currentUserService = currentUserService;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public Optional<UserIdentity> currentUser() {
        return currentUserService.currentUser().map(this::toIdentity);
    }

    @Override
    public UserIdentity requireCurrentUser() {
        return toIdentity(currentUserService.requireCurrentUser());
    }

    @Override
    public String currentOperator() {
        return SecuritySupport.currentOperator();
    }

    @Override
    public boolean isPlatformSuperAdmin() {
        return dataScopeService.isPlatformSuperAdmin();
    }

    @Override
    public Optional<Set<Long>> visibleUserIds(String tenantId) {
        return dataScopeService.visibleUserIds(tenantId);
    }

    @Override
    public boolean canAccessUser(String tenantId, Long userId) {
        return dataScopeService.canAccessUser(tenantId, userId);
    }

    @Override
    public boolean canAccessDept(String tenantId, Long deptId) {
        return dataScopeService.canAccessDept(tenantId, deptId);
    }

    private UserIdentity toIdentity(UserAccount user) {
        return new UserIdentity(user.id(), user.tenantId(), user.username());
    }
}
