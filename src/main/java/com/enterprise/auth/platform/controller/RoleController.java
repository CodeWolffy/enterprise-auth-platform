package com.enterprise.auth.platform.controller;

import com.enterprise.auth.platform.service.CatalogService;
import com.enterprise.auth.platform.common.convention.result.ApiResponse;
import com.enterprise.auth.platform.dto.req.AssignResourcesRequest;
import com.enterprise.auth.platform.dto.req.CreateRoleRequest;
import com.enterprise.auth.platform.dto.req.CreateRoleRequest;
import com.enterprise.auth.platform.service.RoleManagementService;
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

    public RoleController(CatalogService catalogService, RoleManagementService roleManagementService) {
        this.catalogService = catalogService;
        this.roleManagementService = roleManagementService;
    }

    @Operation(summary = "查询角色列表")
    @GetMapping
    @SaCheckPermission("role:read")
    public ApiResponse<List<CatalogService.RoleView>> list() {
        return ApiResponse.ok(catalogService.roles());
    }

    @Operation(summary = "查询角色已分配资源")
    @GetMapping("/{roleId}/resources")
    @SaCheckPermission("role:read")
    public ApiResponse<Set<Long>> assignedResources(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        return ApiResponse.ok(roleManagementService.listAssignedResources(roleId));
    }

    @Operation(summary = "新增角色")
    @PostMapping
    @SaCheckPermission("role:write")
    public ApiResponse<CatalogService.RoleView> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok(roleManagementService.create(request));
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{roleId}")
    @SaCheckPermission("role:write")
    public ApiResponse<CatalogService.RoleView> update(
            @Parameter(description = "角色 ID") @PathVariable Long roleId,
            @Valid @RequestBody CreateRoleRequest request
    ) {
        return ApiResponse.ok(roleManagementService.update(roleId, request));
    }

    @Operation(summary = "分配角色资源")
    @PutMapping("/{roleId}/resources")
    @SaCheckPermission("role:write")
    public ApiResponse<Set<Long>> assignResources(
            @Parameter(description = "角色 ID") @PathVariable Long roleId,
            @Valid @RequestBody AssignResourcesRequest request
    ) {
        return ApiResponse.ok(roleManagementService.assignResources(roleId, request.resourceIds()));
    }

    @Operation(summary = "删除角色")
    @DeleteMapping("/{roleId}")
    @SaCheckPermission("role:write")
    public ApiResponse<Void> delete(@Parameter(description = "角色 ID") @PathVariable Long roleId) {
        roleManagementService.delete(roleId);
        return ApiResponse.ok();
    }
}