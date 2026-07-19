package com.enterprise.auth.platform.modules.system.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.system.application.CategoryRuleApplicationService;
import com.enterprise.auth.platform.modules.system.application.SystemViewModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理")
@RestController
@RequestMapping("/api/system/categories")
public class CategoryController {

    private final CategoryRuleApplicationService categoryRuleApplicationService;

    public CategoryController(CategoryRuleApplicationService categoryRuleApplicationService) {
        this.categoryRuleApplicationService = categoryRuleApplicationService;
    }

    @Operation(summary = "查询系统分类配置")
    @GetMapping
    @SaCheckPermission(PermissionCodes.SYSCATEGORY_PAGE)
    public ApiResponse<Map<String, List<SystemViewModels.CategoryOption>>> categories() {
        return ApiResponse.ok(categoryRuleApplicationService.categories());
    }

    @Operation(summary = "查询指定类型的分类配置")
    @GetMapping("/{targetType}")
    @SaCheckPermission(PermissionCodes.SYSCATEGORY_GET)
    public ApiResponse<List<SystemViewModels.CategoryOption>> categoryOptions(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.categoryOptions(targetType));
    }

    @Operation(summary = "查询分类配置分析")
    @GetMapping("/{targetType}/{code}/analysis")
    @SaCheckPermission(PermissionCodes.SYSCATEGORY_GET)
    public ApiResponse<SystemViewModels.CategoryAnalysis> categoryAnalysis(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.analyzeCategoryOption(targetType, code));
    }

    @SysLog("新增分类配置")
    @Operation(summary = "新增分类配置")
    @PostMapping("/{targetType}")
    @SaCheckPermission(PermissionCodes.SYSCATEGORY_ADD)
    public ApiResponse<SystemViewModels.CategoryOption> createCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Valid @RequestBody CategoryConfigRequest request
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.createCategoryOption(targetType, request));
    }

    @SysLog("修改分类配置")
    @Operation(summary = "修改分类配置")
    @PutMapping("/{targetType}/{code}")
    @SaCheckPermission(PermissionCodes.SYSCATEGORY_EDIT)
    public ApiResponse<SystemViewModels.CategoryOption> updateCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code,
            @Valid @RequestBody CategoryConfigRequest request
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.updateCategoryOption(targetType, code, request));
    }

    @SysLog("删除分类配置")
    @Operation(summary = "删除分类配置")
    @DeleteMapping("/{targetType}/{code}")
    @SaCheckPermission(PermissionCodes.SYSCATEGORY_DEL)
    public ApiResponse<Void> deleteCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code
    ) {
        categoryRuleApplicationService.deleteCategoryOption(targetType, code);
        return ApiResponse.ok();
    }
}
