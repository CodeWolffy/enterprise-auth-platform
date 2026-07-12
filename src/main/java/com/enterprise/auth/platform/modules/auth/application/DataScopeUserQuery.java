package com.enterprise.auth.platform.modules.auth.application;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 数据权限用用户查询端口，由 user 模块实现，auth 不直接依赖 user Mapper。 */
public interface DataScopeUserQuery {

    Optional<ScopedUser> findActive(Long userId, String tenantId);

    List<ScopedUser> listByDeptIds(String tenantId, Set<Long> deptIds);

    record ScopedUser(Long id, String username, Long deptId) {
    }
}