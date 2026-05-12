package com.enterprise.auth.platform.controller;

import com.enterprise.auth.platform.service.CatalogService;
import com.enterprise.auth.platform.common.convention.result.ApiResponse;
import com.enterprise.auth.platform.dto.model.PageResult;
import com.enterprise.auth.platform.dto.req.AssignRolesRequest;
import com.enterprise.auth.platform.dto.req.CreateUserRequest;
import com.enterprise.auth.platform.dto.req.CreateUserRequest;
import com.enterprise.auth.platform.dto.resp.UserSummary;
import com.enterprise.auth.platform.service.UserDirectoryService;
import com.enterprise.auth.platform.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserDirectoryService userDirectoryService;
    private final UserManagementService userManagementService;

    public UserController(UserDirectoryService userDirectoryService, UserManagementService userManagementService) {
        this.userDirectoryService = userDirectoryService;
        this.userManagementService = userManagementService;
    }

    @Operation(summary = "分页查询用户列表")
    @GetMapping
    @SaCheckPermission("user:read")
    public ApiResponse<PageResult<UserSummary>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(userDirectoryService.listUsers(username, mobile, email, enabled, page, size));
    }

    @Operation(summary = "查询用户已分配角色")
    @GetMapping("/{userId}/roles")
    @SaCheckPermission("user:read")
    public ApiResponse<List<CatalogService.RoleView>> assignedRoles(@Parameter(description = "用户 ID") @PathVariable Long userId) {
        return ApiResponse.ok(userManagementService.listAssignedRoles(userId));
    }

    @Operation(summary = "新增用户")
    @PostMapping
    @SaCheckPermission("user:write")
    public ApiResponse<UserSummary> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userManagementService.create(request));
    }

    @Operation(summary = "修改用户")
    @PutMapping("/{userId}")
    @SaCheckPermission("user:write")
    public ApiResponse<UserSummary> update(
            @Parameter(description = "用户 ID") @PathVariable Long userId,
            @Valid @RequestBody CreateUserRequest request
    ) {
        return ApiResponse.ok(userManagementService.update(userId, request));
    }

    @Operation(summary = "分配用户角色")
    @PutMapping("/{userId}/roles")
    @SaCheckPermission("user:write")
    public ApiResponse<UserSummary> assignRoles(
            @Parameter(description = "用户 ID") @PathVariable Long userId,
            @Valid @RequestBody AssignRolesRequest request
    ) {
        return ApiResponse.ok(userManagementService.assignRoles(userId, request.roleCodes()));
    }

    @Operation(summary = "删除用户")
    @DeleteMapping("/{userId}")
    @SaCheckPermission("user:write")
    public ApiResponse<Void> delete(@Parameter(description = "用户 ID") @PathVariable Long userId) {
        userManagementService.delete(userId);
        return ApiResponse.ok();
    }
}
