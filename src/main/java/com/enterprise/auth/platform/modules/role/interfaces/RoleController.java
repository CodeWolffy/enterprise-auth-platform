package com.enterprise.auth.platform.modules.role.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.role.application.RoleGrantQueryFacade;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final CatalogService catalogService;
    private final RoleManagementService roleManagementService;
    private final RoleGrantQueryFacade roleGrantQueryFacade;

    public RoleController(
            CatalogService catalogService,
            RoleManagementService roleManagementService,
            RoleGrantQueryFacade roleGrantQueryFacade
    ) {
        this.catalogService = catalogService;
        this.roleManagementService = roleManagementService;
        this.roleGrantQueryFacade = roleGrantQueryFacade;
    }

    @Operation(summary = "查询角色列表")
    @GetMapping
    @SaCheckPermission(PermissionCodes.ROLE_READ)
    public ApiResponse<List<CatalogService.RoleView>> list() {
        return ApiResponse.ok(catalogService.roles());
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @SaCheckPermission(PermissionCodes.ROLE_WRITE)
    public ApiResponse<CatalogService.RoleView> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(roleManagementService.create(request));
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{roleId}")
    @SaCheckPermission(PermissionCodes.ROLE_WRITE)
    public ApiResponse<CatalogService.RoleView> update(
            @Parameter(description = "角色 ID") @PathVariable Long roleId,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        return ApiResponse.ok(roleManagementService.update(roleId, request));
    }

    @Operation(summary = "查询角色已分配菜单")
    @GetMapping("/{roleId}/menus")
    @SaCheckPermission(PermissionCodes.ROLE_READ)
    public ApiResponse<Set<Long>> assignedMenus(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        return ApiResponse.ok(roleGrantQueryFacade.listRoleMenuIds(roleId));
    }

    @Operation(summary = "分配角色菜单")
    @PutMapping("/{roleId}/menus")
    @SaCheckPermission(PermissionCodes.ROLE_WRITE)
    public ApiResponse<Set<Long>> assignMenus(
            @Parameter(description = "角色 ID") @PathVariable Long roleId,
            @Valid @RequestBody AssignMenusRequest request
    ) {
        return ApiResponse.ok(roleManagementService.assignMenus(roleId, request.menuIds()));
    }

    @Operation(summary = "查询角色删除影响")
    @GetMapping("/{roleId}/impact")
    @SaCheckPermission(PermissionCodes.ROLE_READ)
    public ApiResponse<RoleManagementService.RoleImpactView> impact(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        return ApiResponse.ok(roleManagementService.impact(roleId));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    @SaCheckPermission(PermissionCodes.ROLE_WRITE)
    public ApiResponse<Void> delete(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        roleManagementService.delete(roleId);
        return ApiResponse.ok();
    }
}