package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.tenant.interfaces.TenantPackageCrudRequest;
import com.enterprise.auth.platform.modules.tenant.application.TenantCatalogManagementService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TenantCatalogApplicationService {

    private final TenantCatalogManagementService tenantCatalogManagementService;

    public TenantCatalogApplicationService(TenantCatalogManagementService tenantCatalogManagementService) {
        this.tenantCatalogManagementService = tenantCatalogManagementService;
    }

    public List<TenantCatalogManagementService.TenantPackageView> packages() {
        return tenantCatalogManagementService.packages();
    }

    public TenantCatalogManagementService.TenantPackageView createPackage(TenantPackageCrudRequest request) {
        return tenantCatalogManagementService.createPackage(request);
    }

    public TenantCatalogManagementService.TenantPackageView updatePackage(Long id, TenantPackageCrudRequest request) {
        return tenantCatalogManagementService.updatePackage(id, request);
    }

    public TenantCatalogManagementService.TenantPackageImpactView packageImpact(Long id) {
        return tenantCatalogManagementService.packageImpact(id);
    }

    public void deletePackage(Long id) {
        tenantCatalogManagementService.deletePackage(id);
    }
}