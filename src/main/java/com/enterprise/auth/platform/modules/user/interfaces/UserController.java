package com.enterprise.auth.platform.modules.user.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.role.application.RoleView;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.user.application.UserDirectoryService;
import com.enterprise.auth.platform.modules.user.application.UserManagementService;
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
    @SaCheckPermission(PermissionCodes.SYSUSER_PAGE)
    public ApiResponse<PageResult<UserSummary>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(userDirectoryService.listUsers(username, mobile, email, enabled, deptId, tenantId, page, size));
    }

    @Operation(summary = "查询用户已分配角色")
    @GetMapping("/{userId}/roles")
    @SaCheckPermission(PermissionCodes.SYSUSER_GET)
    public ApiResponse<List<RoleView>> assignedRoles(@Parameter(description = "用户 ID") @PathVariable Long userId) {
        return ApiResponse.ok(userManagementService.listAssignedRoles(userId));
    }

    @SysLog("新增用户")
    @Operation(summary = "新增用户")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSUSER_ADD)
    public ApiResponse<UserSummary> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userManagementService.create(request));
    }

    @SysLog("修改用户")
    @Operation(summary = "修改用户")
    @PutMapping("/{userId}")
    @SaCheckPermission(PermissionCodes.SYSUSER_EDIT)
    public ApiResponse<UserSummary> update(
            @Parameter(description = "用户 ID") @PathVariable Long userId,
            @Valid @RequestBody CreateUserRequest request
    ) {
        return ApiResponse.ok(userManagementService.update(userId, request));
    }

    @SysLog("分配用户角色")
    @Operation(summary = "分配用户角色")
    @PutMapping("/{userId}/roles")
    @SaCheckPermission(PermissionCodes.SYSUSER_EDIT)
    public ApiResponse<UserSummary> assignRoles(
            @Parameter(description = "用户 ID") @PathVariable Long userId,
            @Valid @RequestBody AssignRolesRequest request
    ) {
        return ApiResponse.ok(userManagementService.assignRoles(userId, request.roleCodes()));
    }

    @SysLog("删除用户")
    @Operation(summary = "删除用户")
    @DeleteMapping("/{userId}")
    @SaCheckPermission(PermissionCodes.SYSUSER_DEL)
    public ApiResponse<Void> delete(@Parameter(description = "用户 ID") @PathVariable Long userId) {
        userManagementService.delete(userId);
        return ApiResponse.ok();
    }
}
