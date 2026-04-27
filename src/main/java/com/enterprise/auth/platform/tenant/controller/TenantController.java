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

    private final CatalogService catalogService;
    private final TenantManagementService tenantManagementService;

    public TenantController(CatalogService catalogService, TenantManagementService tenantManagementService) {
        this.catalogService = catalogService;
        this.tenantManagementService = tenantManagementService;
    }

    @Operation(summary = "租户列表")
    @GetMapping
    @SaCheckPermission("tenant:read")
    public ApiResponse<PageResult<CatalogService.TenantView>> list(
            @Parameter(description = "按租户编码或名称搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否平台级租户") @RequestParam(required = false) Boolean platformLevel,
            @Parameter(description = "租户状态：1 启用，0 禁用") @RequestParam(required = false) Integer tenantStatus,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(tenantManagementService.page(keyword, platformLevel, tenantStatus, page, size));
    }

    @Operation(summary = "租户变更历史")
    @GetMapping("/{tenantId}/history")
    @SaCheckPermission("tenant:read")
    public ApiResponse<PageResult<TenantManagementService.TenantChangeView>> history(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Parameter(description = "变更类型") @RequestParam(required = false) String changeType,
            @Parameter(description = "字段键") @RequestParam(required = false) String fieldKey,
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "起始时间（Unix 毫秒时间戳，包含）") @RequestParam(required = false) Long fromEpochMs,
            @Parameter(description = "结束时间（Unix 毫秒时间戳，不包含）") @RequestParam(required = false) Long toEpochMs,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size
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

    @Operation(summary = "租户变更历史摘要")
    @GetMapping("/{tenantId}/history/summary")
    @SaCheckPermission("tenant:read")
    public ApiResponse<TenantManagementService.TenantHistorySummaryView> historySummary(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Parameter(description = "变更类型") @RequestParam(required = false) String changeType,
            @Parameter(description = "字段键") @RequestParam(required = false) String fieldKey,
            @Parameter(description = "操作人") @RequestParam(required = false) String operator,
            @Parameter(description = "起始时间（Unix 毫秒时间戳，包含）") @RequestParam(required = false) Long fromEpochMs,
            @Parameter(description = "结束时间（Unix 毫秒时间戳，不包含）") @RequestParam(required = false) Long toEpochMs
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

    @Operation(summary = "获取租户能力覆盖")
    @GetMapping("/{tenantId}/capability-overrides")
    @SaCheckPermission("tenant:read")
    public ApiResponse<TenantManagementService.TenantCapabilityOverrideView> capabilityOverrides(
            @Parameter(description = "租户编码") @PathVariable String tenantId
    ) {
        return ApiResponse.ok(tenantManagementService.capabilityOverrides(tenantId));
    }

    @Operation(summary = "更新租户能力覆盖")
    @PutMapping("/{tenantId}/capability-overrides")
    @SaCheckPermission("tenant:write")
    public ApiResponse<TenantManagementService.TenantCapabilityOverrideView> updateCapabilityOverrides(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantCapabilityOverridesRequest request
    ) {
        return ApiResponse.ok(tenantManagementService.updateCapabilityOverrides(tenantId, request));
    }

    @Operation(summary = "创建租户")
    @PostMapping
    @SaCheckPermission("tenant:write")
    public ApiResponse<CatalogService.TenantView> create(@Valid @RequestBody CreateTenantRequest request) {
        return ApiResponse.ok(tenantManagementService.create(request));
    }

    @Operation(summary = "更新租户")
    @PutMapping("/{tenantId}")
    @SaCheckPermission("tenant:write")
    public ApiResponse<CatalogService.TenantView> update(
            @Parameter(description = "租户编码") @PathVariable String tenantId,
            @Valid @RequestBody UpdateTenantRequest request
    ) {
        return ApiResponse.ok(tenantManagementService.update(tenantId, request));
    }

    @Operation(summary = "删除租户")
    @DeleteMapping("/{tenantId}")
    @SaCheckPermission("tenant:write")
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
