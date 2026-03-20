package com.enterprise.auth.platform.role.controller;

import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.role.dto.AssignPermissionsRequest;
import com.enterprise.auth.platform.role.dto.CreateRoleRequest;
import com.enterprise.auth.platform.role.dto.UpdateRoleRequest;
import com.enterprise.auth.platform.role.service.RoleManagementService;
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

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final CatalogService catalogService;
    private final RoleManagementService roleManagementService;

    public RoleController(CatalogService catalogService, RoleManagementService roleManagementService) {
        this.catalogService = catalogService;
        this.roleManagementService = roleManagementService;
    }

    @Operation(summary = "查询角色列表")
    @GetMapping
    @PreAuthorize("hasAuthority('role:read')")
    public ApiResponse<List<CatalogService.RoleView>> list() {
        return ApiResponse.ok(catalogService.roles());
    }

    @Operation(summary = "查询角色已分配权限")
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('permission:read')")
    public ApiResponse<List<CatalogService.PermissionView>> assignedPermissions(
            @Parameter(description = "角色ID") @PathVariable Long roleId
    ) {
        return ApiResponse.ok(roleManagementService.listAssignedPermissions(roleId));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @PreAuthorize("hasAuthority('role:write')")
    public ApiResponse<CatalogService.RoleView> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(roleManagementService.create(request));
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:write')")
    public ApiResponse<CatalogService.RoleView> update(
            @Parameter(description = "角色ID") @PathVariable Long roleId,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        return ApiResponse.ok(roleManagementService.update(roleId, request));
    }

    @Operation(summary = "分配角色权限")
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasAuthority('role:write')")
    public ApiResponse<List<CatalogService.PermissionView>> assignPermissions(
            @Parameter(description = "角色ID") @PathVariable Long roleId,
            @Valid @RequestBody AssignPermissionsRequest request
    ) {
        return ApiResponse.ok(roleManagementService.assignPermissions(roleId, request.permissionCodes()));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('role:write')")
    public ApiResponse<Void> delete(@Parameter(description = "角色ID") @PathVariable Long roleId) {
        roleManagementService.delete(roleId);
        return ApiResponse.ok();
    }
}
