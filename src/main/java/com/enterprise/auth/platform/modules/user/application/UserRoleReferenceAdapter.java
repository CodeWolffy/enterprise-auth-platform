package com.enterprise.auth.platform.modules.user.application;

import com.enterprise.auth.platform.modules.iam.api.IamRoleUserReferencePort;
import java.util.List;
import org.springframework.stereotype.Component;

/** Resolves user references required by role management. */
@Component
public final class UserRoleReferenceAdapter implements IamRoleUserReferencePort {

    private final UserQueryFacade userQueryFacade;

    public UserRoleReferenceAdapter(UserQueryFacade userQueryFacade) {
        this.userQueryFacade = userQueryFacade;
    }

    @Override
    public List<Long> listUserIdsByRole(String tenantId, Long roleId) {
        return userQueryFacade.listUserIdsByRole(tenantId, roleId);
    }

    @Override
    public long countUsersByRole(String tenantId, Long roleId) {
        return userQueryFacade.countUsersByRole(tenantId, roleId);
    }

    @Override
    public List<RoleUser> findByIds(List<Long> userIds) {
        return userQueryFacade.findByIds(userIds).stream()
                .map(user -> new RoleUser(user.getId(), user.getTenantId(), user.getUsername()))
                .toList();
    }
}
