package com.enterprise.auth.platform.tenant.controller;

import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.dto.CreateTenantRequest;
import com.enterprise.auth.platform.tenant.dto.UpdateTenantCapabilityOverridesRequest;
import com.enterprise.auth.platform.tenant.dto.UpdateTenantRequest;
import com.enterprise.auth.platform.tenant.service.TenantManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Tenant Management")
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final CatalogService catalogService;
    private final TenantManagementService tenantManagementService;

    public TenantController(CatalogService catalogService, TenantManagementService tenantManagementService) {
        this.catalogService = catalogService;
        this.tenantManagementService = tenantManagementService;
    }

    @Operation(summary = "List tenants")
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<PageResult<CatalogService.TenantView>> list(
            @Parameter(description = "Keyword matched against tenant code or name") @RequestParam(required = false) String keyword,
            @Parameter(description = "Whether the tenant is platform level") @RequestParam(required = false) Boolean platformLevel,
            @Parameter(description = "Tenant status: 1 enabled, 0 disabled") @RequestParam(required = false) Integer tenantStatus,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(tenantManagementService.page(keyword, platformLevel, tenantStatus, page, size));
    }

    @Operation(summary = "List tenant change history")
    @GetMapping("/{tenantId}/history")
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<PageResult<TenantManagementService.TenantChangeView>> history(
            @Parameter(description = "Tenant id") @PathVariable String tenantId,
            @Parameter(description = "Change type") @RequestParam(required = false) String changeType,
            @Parameter(description = "Field key") @RequestParam(required = false) String fieldKey,
            @Parameter(description = "Operator") @RequestParam(required = false) String operator,
            @Parameter(description = "Inclusive lower bound, unix epoch milliseconds") @RequestParam(required = false) Long fromEpochMs,
            @Parameter(description = "Exclusive upper bound, unix epoch milliseconds") @RequestParam(required = false) Long toEpochMs,
            @Parameter(description = "Page number") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(tenantManagementService.history(
                tenantId,
                changeType,
                fieldKey,
                operator,
                fromEpochMs,
                toEpochMs,
                page,
                size
        ));
    }

    @Operation(summary = "Get tenant change history summary")
    @GetMapping("/{tenantId}/history/summary")
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<TenantManagementService.TenantHistorySummaryView> historySummary(
            @Parameter(description = "Tenant id") @PathVariable String tenantId,
            @Parameter(description = "Change type") @RequestParam(required = false) String changeType,
            @Parameter(description = "Field key") @RequestParam(required = false) String fieldKey,
            @Parameter(description = "Operator") @RequestParam(required = false) String operator,
            @Parameter(description = "Inclusive lower bound, unix epoch milliseconds") @RequestParam(required = false) Long fromEpochMs,
            @Parameter(description = "Exclusive upper bound, unix epoch milliseconds") @RequestParam(required = false) Long toEpochMs
    ) {
        return ApiResponse.ok(tenantManagementService.historySummary(
                tenantId,
                changeType,
                fieldKey,
                operator,
                fromEpochMs,
                toEpochMs
        ));
    }

    @Operation(summary = "Get tenant capability overrides")
    @GetMapping("/{tenantId}/capability-overrides")
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<TenantManagementService.TenantCapabilityOverrideView> capabilityOverrides(
            @Parameter(description = "Tenant id") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(tenantManagementService.capabilityOverrides(tenantId));
    }

    @Operation(summary = "Update tenant capability overrides")
    @PutMapping("/{tenantId}/capability-overrides")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<TenantManagementService.TenantCapabilityOverrideView> updateCapabilityOverrides(
            @Parameter(description = "Tenant id") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantCapabilityOverridesRequest request
    ) {
        return ApiResponse.ok(tenantManagementService.updateCapabilityOverrides(tenantId, request));
    }

    @Operation(summary = "Create tenant")
    @PostMapping
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<CatalogService.TenantView> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(tenantManagementService.create(request));
    }

    @Operation(summary = "Update tenant")
    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<CatalogService.TenantView> update(
            @Parameter(description = "Tenant id") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return ApiResponse.ok(tenantManagementService.update(tenantId, request));
    }

    @Operation(summary = "Delete tenant")
    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<Void> delete(@Parameter(description = "Tenant id") @PathVariable String tenantId) {
        tenantManagementService.delete(tenantId);
        return ApiResponse.ok();
    }

    @Operation(summary = "Get current tenant context")
    @GetMapping("/current")
    public ApiResponse<Map<String, String>> current() {
        return ApiResponse.ok(Map.of("tenantId", TenantContext.getTenantId()));
    }
}
