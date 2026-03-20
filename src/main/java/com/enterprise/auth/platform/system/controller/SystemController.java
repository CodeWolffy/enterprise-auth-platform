package com.enterprise.auth.platform.system.controller;

import com.enterprise.auth.platform.common.api.ApiResponse;
import com.enterprise.auth.platform.common.model.PageResult;
import com.enterprise.auth.platform.config.FeatureToggleProperties;
import com.enterprise.auth.platform.system.dto.ConfigCrudRequest;
import com.enterprise.auth.platform.system.dto.CategoryConfigRequest;
import com.enterprise.auth.platform.system.dto.DictCrudRequest;
import com.enterprise.auth.platform.system.dto.NoticeCrudRequest;
import com.enterprise.auth.platform.system.service.SystemManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/system")
public class SystemController {

    private final FeatureToggleProperties featureToggleProperties;
    private final SystemManagementService systemManagementService;

    public SystemController(
            FeatureToggleProperties featureToggleProperties,
            SystemManagementService systemManagementService
    ) {
        this.featureToggleProperties = featureToggleProperties;
        this.systemManagementService = systemManagementService;
    }

    @Operation(summary = "查询预留组件开关")
    @GetMapping("/features")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<Map<String, Boolean>> features() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("gatewayEnabled", featureToggleProperties.gatewayEnabled());
        features.put("nacosEnabled", featureToggleProperties.nacosEnabled());
        features.put("mqEnabled", featureToggleProperties.mqEnabled());
        features.put("seataEnabled", featureToggleProperties.seataEnabled());
        features.put("jobEnabled", featureToggleProperties.jobEnabled());
        features.put("lokiEnabled", featureToggleProperties.lokiEnabled());
        return ApiResponse.ok(features);
    }

    @Operation(summary = "查询系统分类配置")
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<Map<String, List<SystemManagementService.CategoryOption>>> categories() {
        return ApiResponse.ok(systemManagementService.categories());
    }

    @Operation(summary = "查询指定类型的分类配置")
    @GetMapping("/categories/{targetType}")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<List<SystemManagementService.CategoryOption>> categoryOptions(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType
    ) {
        return ApiResponse.ok(systemManagementService.categoryOptions(targetType));
    }

    @Operation(summary = "新增分类配置")
    @PostMapping("/categories/{targetType}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.CategoryOption> createCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Valid @RequestBody CategoryConfigRequest request
    ) {
        return ApiResponse.ok(systemManagementService.createCategoryOption(targetType, request));
    }

    @Operation(summary = "修改分类配置")
    @PutMapping("/categories/{targetType}/{code}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.CategoryOption> updateCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code,
            @Valid @RequestBody CategoryConfigRequest request
    ) {
        return ApiResponse.ok(systemManagementService.updateCategoryOption(targetType, code, request));
    }

    @Operation(summary = "删除分类配置")
    @DeleteMapping("/categories/{targetType}/{code}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<Void> deleteCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code
    ) {
        systemManagementService.deleteCategoryOption(targetType, code);
        return ApiResponse.ok();
    }

    @Operation(summary = "分页查询字典列表")
    @GetMapping("/dicts")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<PageResult<SystemManagementService.DictView>> dicts(
            @Parameter(description = "字典类型") @RequestParam(required = false) String dictType,
            @Parameter(description = "字典分类，按字典类型前缀匹配") @RequestParam(required = false) String category,
            @Parameter(description = "关键字，匹配字典编码或字典值") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：createdAt、dictType、dictCode") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(systemManagementService.dicts(dictType, category, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "新增字典")
    @PostMapping("/dicts")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.DictView> createDict(@Valid @RequestBody DictCrudRequest request) {
        return ApiResponse.ok(systemManagementService.createDict(request));
    }

    @Operation(summary = "修改字典")
    @PutMapping("/dicts/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.DictView> updateDict(
            @Parameter(description = "字典 ID") @PathVariable Long id,
            @Valid @RequestBody DictCrudRequest request
    ) {
        return ApiResponse.ok(systemManagementService.updateDict(id, request));
    }

    @Operation(summary = "删除字典")
    @DeleteMapping("/dicts/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<Void> deleteDict(@Parameter(description = "字典 ID") @PathVariable Long id) {
        systemManagementService.deleteDict(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "分页查询参数列表")
    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<PageResult<SystemManagementService.ConfigView>> configs(
            @Parameter(description = "参数分类，按参数键前缀匹配") @RequestParam(required = false) String category,
            @Parameter(description = "关键字，匹配参数键、参数名称或参数值") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：createdAt、configKey、configName") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(systemManagementService.configs(category, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "新增参数")
    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.ConfigView> createConfig(@Valid @RequestBody ConfigCrudRequest request) {
        return ApiResponse.ok(systemManagementService.createConfig(request));
    }

    @Operation(summary = "修改参数")
    @PutMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.ConfigView> updateConfig(
            @Parameter(description = "参数 ID") @PathVariable Long id,
            @Valid @RequestBody ConfigCrudRequest request
    ) {
        return ApiResponse.ok(systemManagementService.updateConfig(id, request));
    }

    @Operation(summary = "删除参数")
    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<Void> deleteConfig(@Parameter(description = "参数 ID") @PathVariable Long id) {
        systemManagementService.deleteConfig(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "分页查询公告列表")
    @GetMapping("/notices")
    @PreAuthorize("hasAuthority('system:read')")
    public ApiResponse<PageResult<SystemManagementService.NoticeView>> notices(
            @Parameter(description = "是否已发布") @RequestParam(required = false) Boolean published,
            @Parameter(description = "工作流状态：DRAFT、SCHEDULED、PUBLISHED") @RequestParam(required = false) String workflowStatus,
            @Parameter(description = "关键字，匹配标题或内容") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：publishTime、createdAt、noticeTitle") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(systemManagementService.notices(published, workflowStatus, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "新增公告")
    @PostMapping("/notices")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.NoticeView> createNotice(@Valid @RequestBody NoticeCrudRequest request) {
        return ApiResponse.ok(systemManagementService.createNotice(request));
    }

    @Operation(summary = "修改公告")
    @PutMapping("/notices/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<SystemManagementService.NoticeView> updateNotice(
            @Parameter(description = "公告 ID") @PathVariable Long id,
            @Valid @RequestBody NoticeCrudRequest request
    ) {
        return ApiResponse.ok(systemManagementService.updateNotice(id, request));
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/notices/{id}")
    @PreAuthorize("hasAuthority('system:write')")
    public ApiResponse<Void> deleteNotice(@Parameter(description = "公告 ID") @PathVariable Long id) {
        systemManagementService.deleteNotice(id);
        return ApiResponse.ok();
    }
}
