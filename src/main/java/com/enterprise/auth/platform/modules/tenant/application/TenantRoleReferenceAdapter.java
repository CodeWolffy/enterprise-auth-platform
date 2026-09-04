package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.role.api.RoleTenantReferencePort;
import org.springframework.stereotype.Component;

/** Resolves tenant references required by role management. */
@Component
public final class TenantRoleReferenceAdapter implements RoleTenantReferencePort {

    private final TenantProfileFacade tenantProfileFacade;

    public TenantRoleReferenceAdapter(TenantProfileFacade tenantProfileFacade) {
        this.tenantProfileFacade = tenantProfileFacade;
    }

    @Override
    public boolean tenantExists(String tenantId) {
        return tenantProfileFacade.findByTenantId(tenantId).isPresent();
    }
}
