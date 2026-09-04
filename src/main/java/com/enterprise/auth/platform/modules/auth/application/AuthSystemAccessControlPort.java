package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.system.api.SystemAccessControlPort;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Adapts auth-owned operator and data-scope behavior to the system module. */
@Component
public final class AuthSystemAccessControlPort implements SystemAccessControlPort {

    private final DataScopeService dataScopeService;

    public AuthSystemAccessControlPort(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public String currentOperator() {
        return SecuritySupport.currentOperator();
    }

    @Override
    public Optional<Set<String>> visibleUsernames(String tenantId) {
        return dataScopeService.visibleUsernames(tenantId);
    }

    @Override
    public boolean canAccessCreatedBy(String tenantId, String createdBy) {
        return dataScopeService.canAccessCreatedBy(tenantId, createdBy);
    }

    @Override
    public String currentScopeCacheKey() {
        return dataScopeService.currentUser()
                .map(user -> user.username() + "|" + user.dataScopeType() + "|"
                        + user.customDeptIds().stream().sorted().toList())
                .orElse("anonymous");
    }
}
