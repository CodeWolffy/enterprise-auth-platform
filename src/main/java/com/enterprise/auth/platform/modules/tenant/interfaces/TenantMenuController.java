package com.enterprise.auth.platform.modules.tenant.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.tenant.application.TenantMenuService;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "租户菜单分配")
@RestController
@RequestMapping("/api/tenant-menus")
public class TenantMenuController {

    private final TenantMenuService tenantMenuService;

    public TenantMenuController(TenantMenuService tenantMenuService) {
        this.tenantMenuService = tenantMenuService;
    }

    @Operation(summary = "查询租户分配的菜单ID集合")
    @GetMapping("/{tenantId}")
    @SaCheckPermission(PermissionCodes.SYSTENANT_GET)
    public ApiResponse<Set<Long>> getTenantMenus(
            @Parameter(description = "租户ID") @PathVariable String tenantId) {
        return ApiResponse.ok(tenantMenuService.findTenantMenuIds(tenantId));
    }

    @SysLog("保存租户菜单分配")
    @Operation(summary = "保存租户菜单分配（全量替换）")
    @PostMapping("/{tenantId}")
    @SaCheckPermission(PermissionCodes.SYSTENANT_EDIT)
    public ApiResponse<Void> saveTenantMenus(
            @Parameter(description = "租户ID") @PathVariable String tenantId,
            @Valid @RequestBody AssignTenantMenusRequest request) {
        tenantMenuService.saveTenantMenu(tenantId, request.menuIds());
        return ApiResponse.ok();
    }
}