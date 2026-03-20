package com.enterprise.auth.platform.permission.controller;

import com.enterprise.auth.platform.catalog.CatalogService;
import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.model.MenuItem;
import com.enterprise.auth.platform.permission.dto.CreatePermissionRequest;
import com.enterprise.auth.platform.permission.dto.UpdatePermissionRequest;
import com.enterprise.auth.platform.permission.service.PermissionManagementService;
import com.enterprise.auth.platform.user.model.UserAccount;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "权限管理")
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final CatalogService catalogService;
    private final PermissionManagementService permissionManagementService;

    public PermissionController(
            CatalogService catalogService,
            PermissionManagementService permissionManagementService
    ) {
        this.catalogService = catalogService;
        this.permissionManagementService = permissionManagementService;
    }

    @Operation(summary = "查询权限列表")
    @GetMapping
    @PreAuthorize("hasAuthority('permission:read')")
    public ApiResponse<List<CatalogService.PermissionView>> list() {
        return ApiResponse.ok(catalogService.permissions());
    }

    @Operation(summary = "查询当前用户菜单视图")
    @GetMapping("/menus")
    @PreAuthorize("hasAuthority('permission:read')")
    public ApiResponse<List<MenuItem>> menus(@AuthenticationPrincipal UserAccount currentUser) {
        return ApiResponse.ok(catalogService.menusFor(currentUser.permissions()));
    }

    @Operation(summary = "新增权限")
    @PostMapping
    @PreAuthorize("hasAuthority('permission:write')")
    public ApiResponse<CatalogService.PermissionView> create(@Valid @RequestBody CreatePermissionRequest request) {
        return ApiResponse.ok(permissionManagementService.create(request));
    }

    @Operation(summary = "修改权限")
    @PutMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('permission:write')")
    public ApiResponse<CatalogService.PermissionView> update(
            @Parameter(description = "权限ID") @PathVariable Long permissionId,
            @Valid @RequestBody UpdatePermissionRequest request
    ) {
        return ApiResponse.ok(permissionManagementService.update(permissionId, request));
    }

    @Operation(summary = "删除权限")
    @DeleteMapping("/{permissionId}")
    @PreAuthorize("hasAuthority('permission:write')")
    public ApiResponse<Void> delete(@Parameter(description = "权限ID") @PathVariable Long permissionId) {
        permissionManagementService.delete(permissionId);
        return ApiResponse.ok();
    }
}
