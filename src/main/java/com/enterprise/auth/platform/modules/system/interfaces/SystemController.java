package com.enterprise.auth.platform.modules.system.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.modules.system.interfaces.ConfigCrudRequest;
import com.enterprise.auth.platform.modules.system.interfaces.CategoryConfigRequest;
import com.enterprise.auth.platform.modules.system.interfaces.DictCrudRequest;
import com.enterprise.auth.platform.modules.system.interfaces.DictValueCrudRequest;
import com.enterprise.auth.platform.modules.system.interfaces.NoticeCrudRequest;
import com.enterprise.auth.platform.modules.system.application.CategoryRuleApplicationService;
import com.enterprise.auth.platform.modules.system.application.ConfigApplicationService;
import com.enterprise.auth.platform.modules.system.application.DictApplicationService;
import com.enterprise.auth.platform.modules.system.application.DictValueApplicationService;
import com.enterprise.auth.platform.modules.system.application.NoticeApplicationService;
import com.enterprise.auth.platform.modules.system.application.SystemViewModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@Tag(name = "系统管理")
@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final DictApplicationService dictApplicationService;
    private final DictValueApplicationService dictValueApplicationService;
    private final ConfigApplicationService configApplicationService;
    private final NoticeApplicationService noticeApplicationService;
    private final CategoryRuleApplicationService categoryRuleApplicationService;

    public SystemController(
            DictApplicationService dictApplicationService,
            DictValueApplicationService dictValueApplicationService,
            ConfigApplicationService configApplicationService,
            NoticeApplicationService noticeApplicationService,
            CategoryRuleApplicationService categoryRuleApplicationService
    ) {
        this.dictApplicationService = dictApplicationService;
        this.dictValueApplicationService = dictValueApplicationService;
        this.configApplicationService = configApplicationService;
        this.noticeApplicationService = noticeApplicationService;
        this.categoryRuleApplicationService = categoryRuleApplicationService;
    }

    @Operation(summary = "查询预留组件状态")
    @GetMapping("/features")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<Map<String, Boolean>> features() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("gatewayEnabled", false);
        features.put("nacosEnabled", false);
        features.put("mqEnabled", false);
        features.put("seataEnabled", false);
        features.put("jobEnabled", false);
        features.put("lokiEnabled", false);
        return ApiResponse.ok(features);
    }

    @Operation(summary = "查询系统分类配置")
    @GetMapping("/categories")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<Map<String, List<SystemViewModels.CategoryOption>>> categories() {
        return ApiResponse.ok(categoryRuleApplicationService.categories());
    }

    @Operation(summary = "查询指定类型的分类配置")
    @GetMapping("/categories/{targetType}")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<List<SystemViewModels.CategoryOption>> categoryOptions(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.categoryOptions(targetType));
    }

    @Operation(summary = "查询分类配置分析")
    @GetMapping("/categories/{targetType}/{code}/analysis")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<SystemViewModels.CategoryAnalysis> categoryAnalysis(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.analyzeCategoryOption(targetType, code));
    }

    @Operation(summary = "新增分类配置")
    @PostMapping("/categories/{targetType}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.CategoryOption> createCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Valid @RequestBody CategoryConfigRequest request
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.createCategoryOption(targetType, request));
    }

    @Operation(summary = "修改分类配置")
    @PutMapping("/categories/{targetType}/{code}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.CategoryOption> updateCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code,
            @Valid @RequestBody CategoryConfigRequest request
    ) {
        return ApiResponse.ok(categoryRuleApplicationService.updateCategoryOption(targetType, code, request));
    }

    @Operation(summary = "删除分类配置")
    @DeleteMapping("/categories/{targetType}/{code}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<Void> deleteCategoryOption(
            @Parameter(description = "分类目标类型：dict 或 config") @PathVariable String targetType,
            @Parameter(description = "分类编码") @PathVariable String code
    ) {
        categoryRuleApplicationService.deleteCategoryOption(targetType, code);
        return ApiResponse.ok();
    }

    @Operation(summary = "分页查询字典列表")
    @GetMapping("/dicts")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<PageResult<SystemViewModels.DictView>> dicts(
            @Parameter(description = "字典类型") @RequestParam(required = false) String dictType,
            @Parameter(description = "字典分类，按字典类型前缀匹配") @RequestParam(required = false) String category,
            @Parameter(description = "关键字，匹配字典编码或字典值") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：createdAt、dictType、dictCode") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(dictApplicationService.dicts(dictType, category, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "查询字典详情")
    @GetMapping("/dicts/{id}")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<SystemViewModels.DictDetailView> dictDetail(@Parameter(description = "字典 ID") @PathVariable Long id) {
        return ApiResponse.ok(dictApplicationService.detail(id));
    }

    @Operation(summary = "新增字典")
    @PostMapping("/dicts")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.DictView> createDict(@Valid @RequestBody DictCrudRequest request) {
        return ApiResponse.ok(dictApplicationService.createDict(request));
    }

    @Operation(summary = "修改字典")
    @PutMapping("/dicts/{id}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.DictView> updateDict(
            @Parameter(description = "字典 ID") @PathVariable Long id,
            @Valid @RequestBody DictCrudRequest request
    ) {
        return ApiResponse.ok(dictApplicationService.updateDict(id, request));
    }

    @Operation(summary = "删除字典")
    @DeleteMapping("/dicts/{id}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<Void> deleteDict(@Parameter(description = "字典 ID") @PathVariable Long id) {
        dictApplicationService.deleteDict(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "按字典类型查询字典值列表")
    @GetMapping("/dicts/values")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<List<SystemViewModels.DictValueView>> dictValues(
            @Parameter(description = "字典类型") @RequestParam String dictType
    ) {
        return ApiResponse.ok(dictValueApplicationService.listByType(dictType));
    }

    @Operation(summary = "查询字典值列表")
    @GetMapping("/dicts/{id}/values")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<List<SystemViewModels.DictValueView>> dictValuesByDict(
            @Parameter(description = "字典 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(dictValueApplicationService.listByDictId(id));
    }

    @Operation(summary = "新增字典值")
    @PostMapping("/dicts/{id}/values")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.DictValueView> createDictValue(
            @Parameter(description = "字典 ID") @PathVariable Long id,
            @Valid @RequestBody DictValueCrudRequest request
    ) {
        return ApiResponse.ok(dictValueApplicationService.create(id, request));
    }

    @Operation(summary = "修改字典值")
    @PutMapping("/dict-values/{valueId}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.DictValueView> updateDictValue(
            @Parameter(description = "字典值 ID") @PathVariable Long valueId,
            @Valid @RequestBody DictValueCrudRequest request
    ) {
        return ApiResponse.ok(dictValueApplicationService.update(valueId, request));
    }

    @Operation(summary = "删除字典值")
    @DeleteMapping("/dict-values/{valueId}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<Void> deleteDictValue(@Parameter(description = "字典值 ID") @PathVariable Long valueId) {
        dictValueApplicationService.delete(valueId);
        return ApiResponse.ok();
    }

    @Operation(summary = "刷新字典缓存")
    @DeleteMapping("/dicts/cache")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<String> evictDictCache() {
        return ApiResponse.ok(dictValueApplicationService.refreshCache());
    }

    @Operation(summary = "分页查询参数列表")
    @GetMapping("/configs")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
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

    @Operation(summary = "新增参数")
    @PostMapping("/configs")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.ConfigView> createConfig(@Valid @RequestBody ConfigCrudRequest request) {
        return ApiResponse.ok(configApplicationService.createConfig(request));
    }

    @Operation(summary = "修改参数")
    @PutMapping("/configs/{id}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.ConfigView> updateConfig(
            @Parameter(description = "参数 ID") @PathVariable Long id,
            @Valid @RequestBody ConfigCrudRequest request
    ) {
        return ApiResponse.ok(configApplicationService.updateConfig(id, request));
    }

    @Operation(summary = "删除参数")
    @DeleteMapping("/configs/{id}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<Void> deleteConfig(@Parameter(description = "参数 ID") @PathVariable Long id) {
        configApplicationService.deleteConfig(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "分页查询公告列表")
    @GetMapping("/notices")
    @SaCheckPermission(PermissionCodes.SYSTEM_READ)
    public ApiResponse<PageResult<SystemViewModels.NoticeView>> notices(
            @Parameter(description = "是否已发布") @RequestParam(required = false) Boolean published,
            @Parameter(description = "工作流状态：DRAFT、SCHEDULED、PUBLISHED") @RequestParam(required = false) String workflowStatus,
            @Parameter(description = "关键字，匹配标题或内容") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：publishTime、createdAt、noticeTitle") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(noticeApplicationService.notices(published, workflowStatus, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "新增公告")
    @PostMapping("/notices")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.NoticeView> createNotice(@Valid @RequestBody NoticeCrudRequest request) {
        return ApiResponse.ok(noticeApplicationService.createNotice(request));
    }

    @Operation(summary = "修改公告")
    @PutMapping("/notices/{id}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<SystemViewModels.NoticeView> updateNotice(
            @Parameter(description = "公告 ID") @PathVariable Long id,
            @Valid @RequestBody NoticeCrudRequest request
    ) {
        return ApiResponse.ok(noticeApplicationService.updateNotice(id, request));
    }

    @Operation(summary = "删除公告")
    @DeleteMapping("/notices/{id}")
    @SaCheckPermission(PermissionCodes.SYSTEM_WRITE)
    public ApiResponse<Void> deleteNotice(@Parameter(description = "公告 ID") @PathVariable Long id) {
        noticeApplicationService.deleteNotice(id);
        return ApiResponse.ok();
    }
}