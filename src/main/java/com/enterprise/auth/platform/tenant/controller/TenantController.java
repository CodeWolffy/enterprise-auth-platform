package com.enterprise.auth.platform.tenant.controller;

import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.tenant.dto.CreateTenantRequest;
import com.enterprise.auth.platform.tenant.dto.UpdateTenantRequest;
import com.enterprise.auth.platform.tenant.service.TenantManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "租户管理")
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final CatalogService catalogService;
    private final TenantManagementService tenantManagementService;

    public TenantController(CatalogService catalogService, TenantManagementService tenantManagementService) {
        this.catalogService = catalogService;
        this.tenantManagementService = tenantManagementService;
    }

    @Operation(summary = "查询租户列表")
    @GetMapping
    @PreAuthorize("hasAuthority('tenant:read')")
    public ApiResponse<List<CatalogService.TenantView>> list() {
        return ApiResponse.ok(catalogService.tenants());
    }

    @Operation(summary = "新增租户")
    @PostMapping
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<CatalogService.TenantView> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(tenantManagementService.create(request));
    }

    @Operation(summary = "修改租户")
    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<CatalogService.TenantView> update(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return ApiResponse.ok(tenantManagementService.update(tenantId, request));
    }

    @Operation(summary = "删除租户")
    @DeleteMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('tenant:write')")
    public ApiResponse<Void> delete(@Parameter(description = "租户编码") @PathVariable String tenantId) {
        tenantManagementService.delete(tenantId);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取当前租户上下文")
    @GetMapping("/current")
    public ApiResponse<Map<String, String>> current() {
        return ApiResponse.ok(Map.of("tenantId", TenantContext.getTenantId()));
    }
}
