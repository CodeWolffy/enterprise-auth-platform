package com.enterprise.auth.platform.modules.user.application;

import com.enterprise.auth.platform.modules.iam.api.IamDeptUserReferencePort;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Resolves user references required by department management. */
@Component
public final class UserDeptReferenceAdapter implements IamDeptUserReferencePort {

    private final UserQueryFacade userQueryFacade;

    public UserDeptReferenceAdapter(UserQueryFacade userQueryFacade) {
        this.userQueryFacade = userQueryFacade;
    }

    @Override
    public long countByDept(String tenantId, Long deptId) {
        return userQueryFacade.countByDept(tenantId, deptId);
    }

    @Override
    public boolean userExists(String tenantId, Long userId) {
        return userId != null && userQueryFacade.countExistingByIds(tenantId, Set.of(userId)) == 1;
    }
}
