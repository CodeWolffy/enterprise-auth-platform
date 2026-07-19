package com.enterprise.auth.platform.modules.iam.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** IAM data-scope query port implemented by the user module. */
public interface DataScopeUserQuery {

    Optional<ScopedUser> findActive(Long userId, String tenantId);

    List<ScopedUser> listByDeptIds(String tenantId, Set<Long> deptIds);

    record ScopedUser(Long id, String username, Long deptId) {
    }
}
