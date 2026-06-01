package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.tenant.domain.TenantResourceOverrideItem;
import com.enterprise.auth.platform.modules.resource.application.ResourceService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TenantResourcePolicyFacade {

    private final ResourceService resourceService;
    private final TenantAccessPolicy tenantAccessPolicy;

    public TenantResourcePolicyFacade(
            ResourceService resourceService,
            TenantAccessPolicy tenantAccessPolicy
    ) {
        this.resourceService = resourceService;
        this.tenantAccessPolicy = tenantAccessPolicy;
    }

    public List<TenantResourceOverrideItem> listTenantOverrides(String tenantId) {
        tenantAccessPolicy.ensureTenantReadable(tenantId);
        return resourceService.listTenantOverrides(tenantId);
    }
}
