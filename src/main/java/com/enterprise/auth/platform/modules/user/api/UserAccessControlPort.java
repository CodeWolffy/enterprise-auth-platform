package com.enterprise.auth.platform.modules.user.api;

import java.util.Optional;
import java.util.Set;

/** User-facing access-control contract implemented by the IAM/auth context. */
public interface UserAccessControlPort {

    Optional<UserIdentity> currentUser();

    UserIdentity requireCurrentUser();

    String currentOperator();

    boolean isPlatformSuperAdmin();

    Optional<Set<Long>> visibleUserIds(String tenantId);

    boolean canAccessUser(String tenantId, Long userId);

    boolean canAccessDept(String tenantId, Long deptId);

    record UserIdentity(Long id, String tenantId, String username) {
    }
}
