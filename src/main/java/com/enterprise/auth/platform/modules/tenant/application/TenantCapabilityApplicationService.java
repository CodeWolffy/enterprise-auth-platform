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

    public TenantCapabilitySummaryView capabilitySummary(String tenantId) {
        return TenantCapabilitySummaryView.from(tenantManagementService.capabilitySummary(tenantId));
    }

    public TenantCapabilityOverrideView updateCapabilityOverrides(
            String tenantId,
            UpdateTenantCapabilityOverridesRequest request
    ) {
        return TenantCapabilityOverrideView.from(tenantManagementService.updateCapabilityOverrides(tenantId, request));
    }

    @Schema(description = "租户能力摘要")
    public record TenantCapabilitySummaryView(
            @Schema(description = "租户编码") String tenantId,
            @Schema(description = "套餐编码") String packageCode,
            @Schema(description = "套餐名称") String packageName,
            @Schema(description = "套餐默认能力编码集合") List<String> packageCapabilityCodes,
            @Schema(description = "当前生效能力编码集合") List<String> effectiveCapabilityCodes,
            @Schema(description = "相对套餐新增的能力编码集合") List<String> addedCapabilities,
            @Schema(description = "相对套餐禁用的能力编码集合") List<String> disabledCapabilities,
            @Schema(description = "套餐默认能力数量") int packageCapabilityCount,
            @Schema(description = "当前生效能力数量") int effectiveCapabilityCount,
            @Schema(description = "新增能力数量") int addedCapabilityCount,
            @Schema(description = "禁用能力数量") int disabledCapabilityCount,
            @Schema(description = "实际可见菜单") List<ResourceScopeMenuView> visibleMenus,
            @Schema(description = "实际可授权菜单") List<ResourceScopeMenuView> grantableMenus,
            @Schema(description = "实际可见菜单数量") int visibleMenuCount,
            @Schema(description = "实际可授权菜单数量") int grantableMenuCount
    ) {
        static TenantCapabilitySummaryView from(TenantManagementService.TenantCapabilitySummaryView source) {
            return new TenantCapabilitySummaryView(
                    source.tenantId(),
                    source.packageCode(),
                    source.packageName(),
                    source.packageCapabilityCodes(),
                    source.effectiveCapabilityCodes(),
                    source.addedCapabilities(),
                    source.disabledCapabilities(),
                    source.packageCapabilityCount(),
                    source.effectiveCapabilityCount(),
                    source.addedCapabilityCount(),
                    source.disabledCapabilityCount(),
                    source.visibleMenus().stream().map(ResourceScopeMenuView::from).toList(),
                    source.grantableMenus().stream().map(ResourceScopeMenuView::from).toList(),
                    source.visibleMenuCount(),
                    source.grantableMenuCount()
            );
        }
    }

    @Schema(description = "租户资源范围菜单")
    public record ResourceScopeMenuView(
            @Schema(description = "菜单 ID") Long id,
            @Schema(description = "父级菜单 ID") Long parentId,
            @Schema(description = "菜单名称") String menuName,
            @Schema(description = "菜单类型") String menuType,
            @Schema(description = "资源键") String resourceKey,
            @Schema(description = "授权键") String grantKey,
            @Schema(description = "路由路径") String path
    ) {
        static ResourceScopeMenuView from(TenantManagementService.ResourceScopeMenuView source) {
            return new ResourceScopeMenuView(
                    source.id(),
                    source.parentId(),
                    source.menuName(),
                    source.menuType(),
                    source.resourceKey(),
                    source.grantKey(),
                    source.path()
            );
        }
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