package com.enterprise.auth.platform.modules.menu.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.audit.SysLog;
import com.enterprise.auth.platform.modules.menu.application.MenuService;
import com.enterprise.auth.platform.modules.menu.application.MenuTreeUtil;
import com.enterprise.auth.platform.modules.menu.api.MenuAccessControlPort;
import com.enterprise.auth.platform.modules.menu.domain.MenuTreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import cn.dev33.satoken.annotation.SaCheckPermission;
import org.springframework.web.bind.annotation.*;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final MenuAccessControlPort accessControl;

    public MenuController(MenuService menuService, ObjectMapper objectMapper, Validator validator, MenuAccessControlPort accessControl) {
        this.menuService = menuService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.accessControl = accessControl;
    }

    @Operation(summary = "查询菜单树")
    @GetMapping("/tree")
    @SaCheckPermission(PermissionCodes.SYSMENU_PAGE)
    public ApiResponse<List<MenuTreeNode>> tree() {
        return ApiResponse.ok(MenuTreeUtil.buildTree(menuService.templateTree()));
    }

    @Operation(summary = "查询可授权菜单树")
    @GetMapping("/grantable-tree")
    @SaCheckPermission(PermissionCodes.SYSROLE_GET)
    public ApiResponse<List<MenuTreeNode>> grantableTree(@RequestParam(required = false) String tenantId) {
        String effectiveTenantId = accessControl.isPlatformSuperAdmin() && org.springframework.util.StringUtils.hasText(tenantId)
                ? tenantId.trim()
                : TenantContext.getTenantId();
        return ApiResponse.ok(MenuTreeUtil.buildTree(menuService.grantableTree(effectiveTenantId)));
    }

    @Operation(summary = "查询平台菜单模板树")
    @GetMapping("/template-tree")
    @SaCheckPermission(PermissionCodes.SYSMENU_PAGE)
    public ApiResponse<List<MenuTreeNode>> templateTree() {
        return ApiResponse.ok(MenuTreeUtil.buildTree(menuService.templateTree()));
    }

    @Operation(summary = "查询菜单详情")
    @GetMapping("/{menuId}")
    @SaCheckPermission(PermissionCodes.SYSMENU_GET)
    public ApiResponse<MenuTreeNode> detail(@Parameter(description = "菜单 ID") @PathVariable Long menuId) {
        return ApiResponse.ok(menuService.detail(menuId));
    }

    @SysLog("新增菜单")
    @Operation(summary = "新增菜单")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSMENU_ADD)
    public ApiResponse<MenuTreeNode> create(@Valid @RequestBody CreateMenuRequest request) {
        return ApiResponse.ok(menuService.create(request));
    }

    @SysLog("修改菜单")
    @Operation(summary = "修改菜单")
    @PutMapping("/{menuId}")
    @SaCheckPermission(PermissionCodes.SYSMENU_EDIT)
    public ApiResponse<MenuTreeNode> update(
            @Parameter(description = "菜单 ID") @PathVariable Long menuId,
            @RequestBody JsonNode body
    ) {
        CreateMenuRequest request = objectMapper.convertValue(body, CreateMenuRequest.class);
        validateRequest(request);
        return ApiResponse.ok(menuService.update(menuId, request, body.has("parentId")));
    }

    private void validateRequest(CreateMenuRequest request) {
        Set<ConstraintViolation<CreateMenuRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    @SysLog("删除菜单")
    @Operation(summary = "删除菜单")
    @DeleteMapping("/{menuId}")
    @SaCheckPermission(PermissionCodes.SYSMENU_DEL)
    public ApiResponse<Void> delete(@Parameter(description = "菜单 ID") @PathVariable Long menuId) {
        menuService.delete(menuId);
        return ApiResponse.ok();
    }

    @Operation(summary = "批量生成按钮权限")
    @PostMapping("/{menuId}/actions")
    @SaCheckPermission(PermissionCodes.SYSMENU_ADD)
    public ApiResponse<List<MenuTreeNode>> batchActions(
            @Parameter(description = "菜单 ID") @PathVariable Long menuId,
            @Valid @RequestBody BatchMenuActionRequest request
    ) {
        return ApiResponse.ok(menuService.batchCreateActions(menuId, request.actions()));
    }

    @Operation(summary = "修改菜单排序")
    @PutMapping("/{menuId}/sort")
    @SaCheckPermission(PermissionCodes.SYSMENU_EDIT)
    public ApiResponse<MenuTreeNode> sort(
            @Parameter(description = "菜单 ID") @PathVariable Long menuId,
            @Valid @RequestBody SortMenuRequest request
    ) {
        return ApiResponse.ok(menuService.updateSort(menuId, request.sort()));
    }
}
