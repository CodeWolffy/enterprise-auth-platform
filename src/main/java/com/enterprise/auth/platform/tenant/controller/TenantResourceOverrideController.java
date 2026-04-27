package com.enterprise.auth.platform.tenant.controller;

import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.resource.model.TenantResourceOverrideItem;
import com.enterprise.auth.platform.resource.service.ResourceService;
import com.enterprise.auth.platform.tenant.dto.UpdateTenantResourceOverridesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "租户资源覆盖")
@RestController
@RequestMapping("/api/tenants/{tenantId}/resource-overrides")
public class TenantResourceOverrideController {

    private final ResourceService resourceService;

    public TenantResourceOverrideController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @Operation(summary = "查询租户资源覆盖")
    @GetMapping
    @SaCheckPermission("tenant:read")
    public ApiResponse<List<TenantResourceOverrideItem>> list(
            @Parameter(description = "租户 ID") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(resourceService.listTenantOverrides(tenantId));
    }

    @Operation(summary = "更新租户资源覆盖")
    @PutMapping
    @SaCheckPermission("tenant:write")
    public ApiResponse<List<TenantResourceOverrideItem>> update(
            @Parameter(description = "租户 ID") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantResourceOverridesRequest request
    ) {
        return ApiResponse.ok(resourceService.updateTenantOverrides(tenantId, request));
    }
}
