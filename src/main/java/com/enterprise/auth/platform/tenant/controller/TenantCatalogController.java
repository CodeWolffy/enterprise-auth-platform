package com.enterprise.auth.platform.tenant.controller;

import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.tenant.dto.TenantCapabilityCrudRequest;
import com.enterprise.auth.platform.tenant.dto.TenantPackageCrudRequest;
import com.enterprise.auth.platform.tenant.service.TenantCatalogManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "租户目录管理")
@RestController
@RequestMapping("/api/tenant-catalog")
public class TenantCatalogController {

    private final TenantCatalogManagementService tenantCatalogManagementService;

    public TenantCatalogController(TenantCatalogManagementService tenantCatalogManagementService) {
        this.tenantCatalogManagementService = tenantCatalogManagementService;
    }

    @Operation(summary = "查询套餐列表")
    @GetMapping("/packages")
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<List<TenantCatalogManagementService.TenantPackageView>> packages() {
        return ApiResponse.ok(tenantCatalogManagementService.packages());
    }

    @Operation(summary = "新增套餐")
    @PostMapping("/packages")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<TenantCatalogManagementService.TenantPackageView> createPackage(
            @Valid @RequestBody TenantPackageCrudRequest request
    ) {
        return ApiResponse.ok(tenantCatalogManagementService.createPackage(request));
    }

    @Operation(summary = "修改套餐")
    @PutMapping("/packages/{id}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<TenantCatalogManagementService.TenantPackageView> updatePackage(
            @Parameter(description = "套餐主键 ID") @PathVariable Long id,
            @Valid @RequestBody TenantPackageCrudRequest request
    ) {
        return ApiResponse.ok(tenantCatalogManagementService.updatePackage(id, request));
    }

    @Operation(summary = "套餐变更影响分析")
    @GetMapping("/packages/{id}/impact")
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<TenantCatalogManagementService.TenantPackageImpactView> packageImpact(
            @Parameter(description = "套餐主键 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(tenantCatalogManagementService.packageImpact(id));
    }

    @Operation(summary = "删除套餐")
    @DeleteMapping("/packages/{id}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<Void> deletePackage(@Parameter(description = "套餐主键 ID") @PathVariable Long id) {
        tenantCatalogManagementService.deletePackage(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询能力列表")
    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<List<TenantCatalogManagementService.TenantCapabilityView>> capabilities() {
        return ApiResponse.ok(tenantCatalogManagementService.capabilities());
    }

    @Operation(summary = "新增能力")
    @PostMapping("/capabilities")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<TenantCatalogManagementService.TenantCapabilityView> createCapability(
            @Valid @RequestBody TenantCapabilityCrudRequest request
    ) {
        return ApiResponse.ok(tenantCatalogManagementService.createCapability(request));
    }

    @Operation(summary = "修改能力")
    @PutMapping("/capabilities/{id}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<TenantCatalogManagementService.TenantCapabilityView> updateCapability(
            @Parameter(description = "能力主键 ID") @PathVariable Long id,
            @Valid @RequestBody TenantCapabilityCrudRequest request
    ) {
        return ApiResponse.ok(tenantCatalogManagementService.updateCapability(id, request));
    }

    @Operation(summary = "能力变更影响分析")
    @GetMapping("/capabilities/{id}/impact")
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<TenantCatalogManagementService.TenantCapabilityImpactView> capabilityImpact(
            @Parameter(description = "能力主键 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(tenantCatalogManagementService.capabilityImpact(id));
    }

    @Operation(summary = "删除能力")
    @DeleteMapping("/capabilities/{id}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<Void> deleteCapability(@Parameter(description = "能力主键 ID") @PathVariable Long id) {
        tenantCatalogManagementService.deleteCapability(id);
        return ApiResponse.ok();
    }
}
