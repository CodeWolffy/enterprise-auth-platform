package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.user.api.UserTenantReferencePort;
import org.springframework.stereotype.Component;

/** Resolves tenant references required by user management. */
@Component
public final class TenantUserReferenceAdapter implements UserTenantReferencePort {

    private final TenantProfileFacade tenantProfileFacade;

    public TenantUserReferenceAdapter(TenantProfileFacade tenantProfileFacade) {
        this.tenantProfileFacade = tenantProfileFacade;
    }

    @Override
    public boolean tenantExists(String tenantId) {
        return tenantProfileFacade.findByTenantId(tenantId).isPresent();
    }
}
