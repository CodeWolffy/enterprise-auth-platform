package com.enterprise.auth.platform.modules.user.application;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import java.util.HashSet;
import java.util.Set;

public record AuthenticationUser(
        Long id,
        String tenantId,
        String username,
        String password,
        boolean enabled,
        Set<String> roles,
        Set<String> permissions,
        Set<Long> customDeptIds,
        DataScopeType dataScopeType,
        int sessionVersion,
        boolean mustChangePassword,
        java.time.LocalDateTime passwordUpdatedAt
) {

    public AuthenticationUser(
            Long id,
            String tenantId,
            String username,
            String password,
            boolean enabled,
            Set<String> roles,
            Set<String> permissions,
            Set<Long> customDeptIds,
            DataScopeType dataScopeType,
            int sessionVersion
    ) {
        this(id, tenantId, username, password, enabled, roles, permissions, customDeptIds, dataScopeType, sessionVersion, false, null);
    }

    public AuthenticationUser {
        roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
        permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
        customDeptIds = customDeptIds == null ? new HashSet<>() : new HashSet<>(customDeptIds);
    }
}