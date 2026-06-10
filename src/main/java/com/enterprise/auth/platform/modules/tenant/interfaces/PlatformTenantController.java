package com.enterprise.auth.platform.modules.tenant.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.tenant.application.TenantCapabilityApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "平台租户兼容入口")
@RestController
@RequestMapping("/api/platform/tenants")
public class PlatformTenantController {

    private final TenantCapabilityApplicationService tenantCapabilityApplicationService;

    public PlatformTenantController(TenantCapabilityApplicationService tenantCapabilityApplicationService) {
        this.tenantCapabilityApplicationService = tenantCapabilityApplicationService;
    }

    @Operation(summary = "获取租户能力摘要")
    @GetMapping("/{tenantId}/capability-summary")
    @SaCheckPermission(PermissionCodes.TENANT_READ)
    public ApiResponse<TenantCapabilityApplicationService.TenantCapabilitySummaryView> capabilitySummary(
            @Parameter(description = "租户编码") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(tenantCapabilityApplicationService.capabilitySummary(tenantId));
    }
}