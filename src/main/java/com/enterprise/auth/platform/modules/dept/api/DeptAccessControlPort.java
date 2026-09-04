package com.enterprise.auth.platform.modules.dept.api;

import java.util.Optional;
import java.util.Set;

/** Department-owned contract for current-user data-scope decisions. */
public interface DeptAccessControlPort {

    boolean isPlatformSuperAdmin();

    Optional<Set<Long>> visibleDeptIds(String tenantId);

    boolean canAccessDept(String tenantId, Long deptId);

    boolean canAccessUser(String tenantId, Long userId);
}
