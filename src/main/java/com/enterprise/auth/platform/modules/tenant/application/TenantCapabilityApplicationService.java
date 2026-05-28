package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.dto.req.TenantCapabilityCrudRequest;
import com.enterprise.auth.platform.dto.req.UpdateTenantCapabilityOverridesRequest;
import com.enterprise.auth.platform.service.TenantCatalogManagementService;
import com.enterprise.auth.platform.service.TenantManagementService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class TenantCapabilityApplicationService {

    private final TenantCatalogManagementService tenantCatalogManagementService;
    private final TenantManagementService tenantManagementService;

    public TenantCapabilityApplicationService(
            TenantCatalogManagementService tenantCatalogManagementService,
            TenantManagementService tenantManagementService
    ) {
        this.tenantCatalogManagementService = tenantCatalogManagementService;
        this.tenantManagementService = tenantManagementService;
    }

    public List<TenantCatalogManagementService.TenantCapabilityView> capabilities() {
        return tenantCatalogManagementService.capabilities();
    }

    public TenantCatalogManagementService.TenantCapabilityView createCapability(TenantCapabilityCrudRequest request) {
        return tenantCatalogManagementService.createCapability(request);
    }

    public TenantCatalogManagementService.TenantCapabilityView updateCapability(Long id, TenantCapabilityCrudRequest request) {
        return tenantCatalogManagementService.updateCapability(id, request);
    }

    public TenantCatalogManagementService.TenantCapabilityImpactView capabilityImpact(Long id) {
        return tenantCatalogManagementService.capabilityImpact(id);
    }

    public void deleteCapability(Long id) {
        tenantCatalogManagementService.deleteCapability(id);
    }

    public TenantManagementService.TenantCapabilityOverrideView capabilityOverrides(String tenantId) {
        return tenantManagementService.capabilityOverrides(tenantId);
    }

    public TenantManagementService.TenantCapabilityOverrideView updateCapabilityOverrides(
            String tenantId,
            UpdateTenantCapabilityOverridesRequest request
    ) {
        return tenantManagementService.updateCapabilityOverrides(tenantId, request);
    }
}