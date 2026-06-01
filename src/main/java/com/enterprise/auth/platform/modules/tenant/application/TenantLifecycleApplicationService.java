package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.tenant.interfaces.CreateTenantRequest;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.modules.tenant.application.TenantManagementService;
import org.springframework.stereotype.Service;

@Service
public class TenantLifecycleApplicationService {

    private final TenantManagementService tenantManagementService;

    public TenantLifecycleApplicationService(TenantManagementService tenantManagementService) {
        this.tenantManagementService = tenantManagementService;
    }

    public CatalogService.TenantView create(CreateTenantRequest request) {
        return tenantManagementService.create(request);
    }

    public CatalogService.TenantView update(String tenantId, CreateTenantRequest request) {
        return tenantManagementService.update(tenantId, request);
    }

    public void delete(String tenantId) {
        tenantManagementService.delete(tenantId);
    }
}