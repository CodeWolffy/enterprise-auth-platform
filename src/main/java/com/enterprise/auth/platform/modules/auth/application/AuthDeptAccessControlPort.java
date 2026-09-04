package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.dept.api.DeptAccessControlPort;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Exposes auth-owned data-scope decisions through the department contract. */
@Component
public final class AuthDeptAccessControlPort implements DeptAccessControlPort {

    private final DataScopeService dataScopeService;

    public AuthDeptAccessControlPort(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    @Override
    public boolean isPlatformSuperAdmin() {
        return dataScopeService.isPlatformSuperAdmin();
    }

    @Override
    public Optional<Set<Long>> visibleDeptIds(String tenantId) {
        return dataScopeService.visibleDeptIds(tenantId);
    }

    @Override
    public boolean canAccessDept(String tenantId, Long deptId) {
        return dataScopeService.canAccessDept(tenantId, deptId);
    }

    @Override
    public boolean canAccessUser(String tenantId, Long userId) {
        return dataScopeService.canAccessUser(tenantId, userId);
    }
}
