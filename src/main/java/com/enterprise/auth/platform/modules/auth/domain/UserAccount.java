package com.enterprise.auth.platform.modules.auth.domain;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashSet;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserAccount(
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
        String avatarFileKey,
        boolean mustChangePassword,
        java.time.LocalDateTime passwordUpdatedAt
) {

    public UserAccount(
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
        this(id, tenantId, username, password, enabled, roles, permissions, customDeptIds, dataScopeType, sessionVersion, null, false, null);
    }

    public UserAccount {
        roles = roles == null ? new HashSet<>() : new HashSet<>(roles);
        permissions = permissions == null ? new HashSet<>() : new HashSet<>(permissions);
        customDeptIds = customDeptIds == null ? new HashSet<>() : new HashSet<>(customDeptIds);
    }

}
