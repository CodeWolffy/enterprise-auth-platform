package com.enterprise.auth.platform.modules.tenant.application;

import com.enterprise.auth.platform.modules.tenant.interfaces.TenantCapabilityCrudRequest;
import com.enterprise.auth.platform.modules.tenant.interfaces.UpdateTenantCapabilityOverridesRequest;
import com.enterprise.auth.platform.modules.tenant.application.TenantCatalogManagementService;
import com.enterprise.auth.platform.modules.tenant.application.TenantManagementService;
import io.swagger.v3.oas.annotations.media.Schema;
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

    public TenantCapabilityOverrideView capabilityOverrides(String tenantId) {
        return TenantCapabilityOverrideView.from(tenantManagementService.capabilityOverrides(tenantId));
    }

    public TenantCapabilityOverrideView updateCapabilityOverrides(
            String tenantId,
            UpdateTenantCapabilityOverridesRequest request
    ) {
        return TenantCapabilityOverrideView.from(tenantManagementService.updateCapabilityOverrides(tenantId, request));
    }
    @Schema(description = "租户能力覆盖视图")
    public record TenantCapabilityOverrideView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "套餐默认能力编码集合") List<String> packageCapabilityCodes,
            @Schema(description = "当前生效能力编码集合") List<String> effectiveCapabilityCodes,
            @Schema(description = "能力覆盖项") List<CapabilityOverrideItemView> overrides
    ) {
        static TenantCapabilityOverrideView from(TenantManagementService.TenantCapabilityOverrideView source) {
            return new TenantCapabilityOverrideView(
                    source.tenantId(),
                    source.packageCode(),
                    source.packageName(),
                    source.packageCapabilityCodes(),
                    source.effectiveCapabilityCodes(),
                    source.overrides().stream().map(CapabilityOverrideItemView::from).toList()
            );
        }
    }

    @Schema(description = "能力覆盖项视图")
    public record CapabilityOverrideItemView(
            @Schema(description = "能力编码") String capabilityCode,
            @Schema(description = "能力名称") String capabilityName,
            @Schema(description = "基础说明") String capabilityDesc,
            @Schema(description = "套餐是否默认启用") boolean packageEnabled,
            @Schema(description = "覆盖启用状态；为空表示继承套餐默认值") Boolean overrideEnabled,
            @Schema(description = "当前是否生效") boolean effectiveEnabled,
            @Schema(description = "说明覆盖") String capabilityDescOverride,
            @Schema(description = "当前展示说明") String effectiveDesc
    ) {
        static CapabilityOverrideItemView from(TenantManagementService.CapabilityOverrideItemView source) {
            return new CapabilityOverrideItemView(
                    source.capabilityCode(),
                    source.capabilityName(),
                    source.capabilityDesc(),
                    source.packageEnabled(),
                    source.overrideEnabled(),
                    source.effectiveEnabled(),
                    source.capabilityDescOverride(),
                    source.effectiveDesc()
            );
        }
    }
}