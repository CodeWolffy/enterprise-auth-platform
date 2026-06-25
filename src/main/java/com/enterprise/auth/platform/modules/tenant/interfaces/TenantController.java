package com.enterprise.auth.platform.modules.tenant.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.tenant.application.TenantCapabilityApplicationService;
import com.enterprise.auth.platform.modules.tenant.application.TenantChangeLogApplicationService;
import com.enterprise.auth.platform.modules.tenant.application.TenantDirectoryApplicationService;
import com.enterprise.auth.platform.modules.tenant.application.TenantLifecycleApplicationService;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.tenant.interfaces.CreateTenantRequest;
import com.enterprise.auth.platform.modules.tenant.interfaces.UpdateTenantCapabilityOverridesRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "租户管理")
@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantLifecycleApplicationService tenantLifecycleApplicationService;
    private final TenantDirectoryApplicationService tenantDirectoryApplicationService;
    private final TenantCapabilityApplicationService tenantCapabilityApplicationService;
    private final TenantChangeLogApplicationService tenantChangeLogApplicationService;

    public TenantController(
            TenantLifecycleApplicationService tenantLifecycleApplicationService,
            TenantDirectoryApplicationService tenantDirectoryApplicationService,
            TenantCapabilityApplicationService tenantCapabilityApplicationService,
            TenantChangeLogApplicationService tenantChangeLogApplicationService
    ) {
        this.tenantLifecycleApplicationService = tenantLifecycleApplicationService;
        this.tenantDirectoryApplicationService = tenantDirectoryApplicationService;
        this.tenantCapabilityApplicationService = tenantCapabilityApplicationService;
        this.tenantChangeLogApplicationService = tenantChangeLogApplicationService;
    }

    @Operation(summary = "租户列表")
    @GetMapping
    @SaCheckPermission(PermissionCodes.SYSTENANT_PAGE)
    public ApiResponse<PageResult<CatalogService.TenantView>> list(
            @Parameter(description = "按租户编码或名称搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否平台级租户") @RequestParam(required = false) Boolean platformLevel,
            @Parameter(description = "租户状态：1 启用，0 禁用") @RequestParam(required = false) Integer tenantStatus,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(tenantDirectoryApplicationService.page(keyword, platformLevel, tenantStatus, page, size));
    }

    @Operation(summary = "租户变更历史")
    @GetMapping("/{tenantId}/history")
    @SaCheckPermission(PermissionCodes.SYSTENANT_GET)
    public ApiResponse<PageResult<TenantChangeLogApplicationService.TenantChangeView>> history(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Parameter(description = "变更类型") @RequestParam(required = false) String changeType,
            @Parameter(description = "字段键") @RequestParam(required = false) String fieldKey,
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "起始时间（Unix 毫秒时间戳，包含）") @RequestParam(required = false) Long fromEpochMs,
            @Parameter(description = "结束时间（Unix 毫秒时间戳，不包含）") @RequestParam(required = false) Long toEpochMs,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(tenantChangeLogApplicationService.history(
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

    @Operation(summary = "租户变更历史摘要")
    @GetMapping("/{tenantId}/history/summary")
    @SaCheckPermission(PermissionCodes.SYSTENANT_GET)
    public ApiResponse<TenantChangeLogApplicationService.TenantHistorySummaryView> historySummary(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Parameter(description = "变更类型") @RequestParam(required = false) String changeType,
            @Parameter(description = "字段键") @RequestParam(required = false) String fieldKey,
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "起始时间（Unix 毫秒时间戳，包含）") @RequestParam(required = false) Long fromEpochMs,
            @Parameter(description = "结束时间（Unix 毫秒时间戳，不包含）") @RequestParam(required = false) Long toEpochMs
    ) {
        return ApiResponse.ok(tenantChangeLogApplicationService.historySummary(
                tenantId,
                changeType,
                fieldKey,
                operator,
                fromEpochMs,
                toEpochMs
        ));
    }

    @Operation(summary = "获取租户能力摘要")
    @GetMapping("/{tenantId}/capability-summary")
    @SaCheckPermission(PermissionCodes.SYSTENANT_GET)
    public ApiResponse<TenantCapabilityApplicationService.TenantCapabilitySummaryView> capabilitySummary(
            @Parameter(description = "租户编码") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(tenantCapabilityApplicationService.capabilitySummary(tenantId));
    }

    @Operation(summary = "获取租户能力覆盖")
    @GetMapping("/{tenantId}/capability-overrides")
    @SaCheckPermission(PermissionCodes.SYSTENANT_GET)
    public ApiResponse<TenantCapabilityApplicationService.TenantCapabilityOverrideView> capabilityOverrides(
            @Parameter(description = "租户编码") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(tenantCapabilityApplicationService.capabilityOverrides(tenantId));
    }

    @SysLog("更新租户能力覆盖")
    @Operation(summary = "更新租户能力覆盖")
    @PutMapping("/{tenantId}/capability-overrides")
    @SaCheckPermission(PermissionCodes.SYSTENANT_EDIT)
    public ApiResponse<TenantCapabilityApplicationService.TenantCapabilityOverrideView> updateCapabilityOverrides(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantCapabilityOverridesRequest request
    ) {
        return ApiResponse.ok(tenantCapabilityApplicationService.updateCapabilityOverrides(tenantId, request));
    }

    @SysLog("创建租户")
    @Operation(summary = "创建租户")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSTENANT_ADD)
    public ApiResponse<CatalogService.TenantView> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(tenantLifecycleApplicationService.create(request));
    }

    @SysLog("更新租户")
    @Operation(summary = "更新租户")
    @PutMapping("/{tenantId}")
    @SaCheckPermission(PermissionCodes.SYSTENANT_EDIT)
    public ApiResponse<CatalogService.TenantView> update(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Valid @RequestBody CreateTenantRequest request
    ) {
        return ApiResponse.ok(tenantLifecycleApplicationService.update(tenantId, request));
    }

    @SysLog("删除租户")
    @Operation(summary = "删除租户")
    @DeleteMapping("/{tenantId}")
    @SaCheckPermission(PermissionCodes.SYSTENANT_DEL)
    public ApiResponse<Void> delete(@Parameter(description = "租户编码") @PathVariable String tenantId) {
        tenantLifecycleApplicationService.delete(tenantId);
        return ApiResponse.ok();
    }

    @Operation(summary = "获取当前租户上下文")
    @GetMapping("/current")
    @SaCheckPermission(PermissionCodes.SYSTENANT_GET)
    public ApiResponse<Map<String, String>> current() {
        return ApiResponse.ok(Map.of("tenantId", TenantContext.getTenantId()));
    }
}
