package com.enterprise.auth.platform.modules.system.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.system.application.ConfigApplicationService;
import com.enterprise.auth.platform.modules.system.application.SystemViewModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统管理")
@RestController
@RequestMapping("/api/system/configs")
public class ConfigController {

    private final ConfigApplicationService configApplicationService;

    public ConfigController(ConfigApplicationService configApplicationService) {
        this.configApplicationService = configApplicationService;
    }

    @Operation(summary = "分页查询参数列表")
    @GetMapping("/page")
    @SaCheckPermission(PermissionCodes.SYSCONFIG_PAGE)
    public ApiResponse<PageResult<SystemViewModels.ConfigView>> configs(
            @Parameter(description = "参数分类，按参数键前缀匹配") @RequestParam(required = false) String category,
            @Parameter(description = "关键字，匹配参数键、参数名称或参数值") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：createdAt、configKey、configName") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(configApplicationService.configs(category, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "查询参数详情")
    @GetMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SYSCONFIG_GET)
    public ApiResponse<SystemViewModels.ConfigDetailView> configDetail(@Parameter(description = "参数 ID") @PathVariable Long id) {
        return ApiResponse.ok(configApplicationService.detail(id));
    }

    @SysLog("新增参数")
    @Operation(summary = "新增参数")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSCONFIG_ADD)
    public ApiResponse<SystemViewModels.ConfigView> createConfig(@Valid @RequestBody ConfigCrudRequest request) {
        return ApiResponse.ok(configApplicationService.createConfig(request));
    }

    @SysLog("修改参数")
    @Operation(summary = "修改参数")
    @PutMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SYSCONFIG_EDIT)
    public ApiResponse<SystemViewModels.ConfigView> updateConfig(
            @Parameter(description = "参数 ID") @PathVariable Long id,
            @Valid @RequestBody ConfigCrudRequest request
    ) {
        return ApiResponse.ok(configApplicationService.updateConfig(id, request));
    }

    @SysLog("删除参数")
    @Operation(summary = "删除参数")
    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionCodes.SYSCONFIG_DEL)
    public ApiResponse<Void> deleteConfig(@Parameter(description = "参数 ID") @PathVariable Long id) {
        configApplicationService.deleteConfig(id);
        return ApiResponse.ok();
    }

    @SysLog("批量删除参数")
    @Operation(summary = "批量删除参数")
    @DeleteMapping
    @SaCheckPermission(PermissionCodes.SYSCONFIG_DEL)
    public ApiResponse<Void> deleteConfigs(@Parameter(description = "参数 ID，逗号分隔") @RequestParam String ids) {
        List<Long> parsedIds = new ArrayList<>();
        for (String id : ids.split(",")) {
            if (!id.isBlank()) {
                parsedIds.add(Long.parseLong(id.trim()));
            }
        }
        configApplicationService.deleteConfigs(parsedIds);
        return ApiResponse.ok();
    }

    @SysLog("刷新参数缓存")
    @Operation(summary = "刷新参数缓存")
    @DeleteMapping("/cache")
    @SaCheckPermission(PermissionCodes.SYSCONFIG_EDIT)
    public ApiResponse<String> evictConfigCache() {
        return ApiResponse.ok(configApplicationService.refreshCache());
    }
}
