package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.dept.api.DeptTenantReferencePort;
import org.springframework.stereotype.Component;

/** Resolves tenant references required by department management. */
@Component
public final class TenantDeptReferenceAdapter implements DeptTenantReferencePort {

    private final TenantProfileFacade tenantProfileFacade;

    public TenantDeptReferenceAdapter(TenantProfileFacade tenantProfileFacade) {
        this.tenantProfileFacade = tenantProfileFacade;
    }

    @Override
    public boolean tenantExists(String tenantId) {
        return tenantProfileFacade.findByTenantId(tenantId).isPresent();
    }
}
