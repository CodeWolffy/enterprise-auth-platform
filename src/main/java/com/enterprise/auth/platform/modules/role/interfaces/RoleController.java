package com.enterprise.auth.platform.modules.role.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.audit.SysLog;
import com.enterprise.auth.platform.common.idempotent.Idempotent;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
import com.enterprise.auth.platform.modules.role.application.RoleCatalogFacade;
import com.enterprise.auth.platform.modules.role.application.RoleView;
import com.enterprise.auth.platform.modules.role.application.RoleManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
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

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleCatalogFacade roleCatalogFacade;
    private final RoleManagementService roleManagementService;
    private final RoleGrantQueryFacade roleGrantQueryFacade;

    public RoleController(
            RoleCatalogFacade roleCatalogFacade,
            RoleManagementService roleManagementService,
            RoleGrantQueryFacade roleGrantQueryFacade
    ) {
        this.roleCatalogFacade = roleCatalogFacade;
        this.roleManagementService = roleManagementService;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
    }

    @Operation(summary = "查询角色列表")
    @GetMapping
    @SaCheckPermission(PermissionCodes.SYSROLE_PAGE)
    public ApiResponse<PageResult<RoleView>> list(
            @Parameter(description = "角色关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "数据权限范围") @RequestParam(required = false) String dataScopeType,
            @Parameter(description = "租户编码") @RequestParam(required = false) String tenantId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(roleCatalogFacade.rolesPage(keyword, dataScopeType, tenantId, page, size));
    }

    @Operation(summary = "查询角色选项")
    @GetMapping("/options")
    @SaCheckPermission(PermissionCodes.SYSROLE_PAGE)
    public ApiResponse<List<RoleView>> options() {
        return ApiResponse.ok(roleCatalogFacade.roles());
    }

    @SysLog("新增角色")
    @Operation(summary = "新增角色")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSROLE_ADD)
    @Idempotent(prefix = "role:create", key = "#request.roleCode", timeout = 5)
    public ApiResponse<RoleView> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(roleManagementService.create(request));
    }

    @SysLog("修改角色")
    @Operation(summary = "修改角色")
    @PutMapping("/{roleId}")
    @SaCheckPermission(PermissionCodes.SYSROLE_EDIT)
    public ApiResponse<RoleView> update(
            @Parameter(description = "角色 ID") @PathVariable Long roleId,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        return ApiResponse.ok(roleManagementService.update(roleId, request));
    }

    @Operation(summary = "查询角色已分配菜单")
    @GetMapping("/{roleId}/menus")
    @SaCheckPermission(PermissionCodes.SYSROLE_GET)
    public ApiResponse<Set<Long>> assignedMenus(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        return ApiResponse.ok(roleGrantQueryFacade.listRoleMenuIds(roleId));
    }

    @SysLog("分配角色菜单")
    @Operation(summary = "分配角色菜单")
    @PutMapping("/{roleId}/menus")
    @SaCheckPermission(PermissionCodes.SYSROLE_EDIT)
    public ApiResponse<Set<Long>> assignMenus(
            @Parameter(description = "角色 ID") @PathVariable Long roleId,
            @Valid @RequestBody AssignMenusRequest request
    ) {
        return ApiResponse.ok(roleManagementService.assignMenus(roleId, request.menuIds()));
    }

    @Operation(summary = "查询角色删除影响")
    @GetMapping("/{roleId}/impact")
    @SaCheckPermission(PermissionCodes.SYSROLE_GET)
    public ApiResponse<RoleManagementService.RoleImpactView> impact(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        return ApiResponse.ok(roleManagementService.impact(roleId));
    }

    @SysLog("删除角色")
    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    @SaCheckPermission(PermissionCodes.SYSROLE_DEL)
    public ApiResponse<Void> delete(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        roleManagementService.delete(roleId);
        return ApiResponse.ok();
    }
}
