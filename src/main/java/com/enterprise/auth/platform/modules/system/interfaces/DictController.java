package com.enterprise.auth.platform.modules.system.interfaces;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.common.web.PageResult;
import com.enterprise.auth.platform.common.audit.SysLog;
import com.enterprise.auth.platform.modules.system.application.DictApplicationService;
import com.enterprise.auth.platform.modules.system.application.DictValueApplicationService;
import com.enterprise.auth.platform.modules.system.application.SystemViewModels;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
@RequestMapping("/api/system")
public class DictController {

    private final DictApplicationService dictApplicationService;
    private final DictValueApplicationService dictValueApplicationService;

    public DictController(
            DictApplicationService dictApplicationService,
            DictValueApplicationService dictValueApplicationService
    ) {
        this.dictApplicationService = dictApplicationService;
        this.dictValueApplicationService = dictValueApplicationService;
    }

    @Operation(summary = "分页查询字典列表")
    @GetMapping("/dicts")
    @SaCheckPermission(PermissionCodes.SYSDICT_PAGE)
    public ApiResponse<PageResult<SystemViewModels.DictView>> dicts(
            @Parameter(description = "字典类型") @RequestParam(required = false) String dictType,
            @Parameter(description = "字典分类，按字典类型前缀匹配") @RequestParam(required = false) String category,
            @Parameter(description = "关键字，匹配字典类型、说明或备注") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "排序字段：createdAt、dictType") @RequestParam(required = false) String sortBy,
            @Parameter(description = "排序方向：asc 或 desc") @RequestParam(required = false) String sortDirection
    ) {
        return ApiResponse.ok(dictApplicationService.dicts(dictType, category, keyword, page, size, sortBy, sortDirection));
    }

    @Operation(summary = "查询字典详情")
    @GetMapping("/dicts/{id}")
    @SaCheckPermission(PermissionCodes.SYSDICT_GET)
    public ApiResponse<SystemViewModels.DictDetailView> dictDetail(@Parameter(description = "字典 ID") @PathVariable Long id) {
        return ApiResponse.ok(dictApplicationService.detail(id));
    }

    @SysLog("新增字典")
    @Operation(summary = "新增字典")
    @PostMapping("/dicts")
    @SaCheckPermission(PermissionCodes.SYSDICT_ADD)
    public ApiResponse<SystemViewModels.DictView> createDict(@Valid @RequestBody DictCrudRequest request) {
        return ApiResponse.ok(dictApplicationService.createDict(request));
    }

    @SysLog("修改字典")
    @Operation(summary = "修改字典")
    @PutMapping("/dicts/{id}")
    @SaCheckPermission(PermissionCodes.SYSDICT_EDIT)
    public ApiResponse<SystemViewModels.DictView> updateDict(
            @Parameter(description = "字典 ID") @PathVariable Long id,
            @Valid @RequestBody DictCrudRequest request
    ) {
        return ApiResponse.ok(dictApplicationService.updateDict(id, request));
    }

    @SysLog("删除字典")
    @Operation(summary = "删除字典")
    @DeleteMapping("/dicts/{id}")
    @SaCheckPermission(PermissionCodes.SYSDICT_DEL)
    public ApiResponse<Void> deleteDict(@Parameter(description = "字典 ID") @PathVariable Long id) {
        dictApplicationService.deleteDict(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "按字典类型查询字典值列表")
    @GetMapping("/dicts/values")
    @SaCheckPermission(PermissionCodes.SYSDICT_GET)
    public ApiResponse<List<SystemViewModels.DictValueView>> dictValues(
            @Parameter(description = "字典类型") @RequestParam String dictType
    ) {
        return ApiResponse.ok(dictValueApplicationService.listByType(dictType));
    }

    @Operation(summary = "查询字典值列表")
    @GetMapping("/dicts/{id}/values")
    @SaCheckPermission(PermissionCodes.SYSDICT_GET)
    public ApiResponse<List<SystemViewModels.DictValueView>> dictValuesByDict(
            @Parameter(description = "字典 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(dictValueApplicationService.listByDictId(id));
    }

    @SysLog("新增字典值")
    @Operation(summary = "新增字典值")
    @PostMapping("/dicts/{id}/values")
    @SaCheckPermission(PermissionCodes.SYSDICT_ADD)
    public ApiResponse<SystemViewModels.DictValueView> createDictValue(
            @Parameter(description = "字典 ID") @PathVariable Long id,
            @Valid @RequestBody DictValueCrudRequest request
    ) {
        return ApiResponse.ok(dictValueApplicationService.create(id, request));
    }

    @Operation(summary = "查询字典值详情")
    @GetMapping("/dict-values/{valueId}")
    @SaCheckPermission(PermissionCodes.SYSDICT_GET)
    public ApiResponse<SystemViewModels.DictValueView> dictValueDetail(
            @Parameter(description = "字典值 ID") @PathVariable Long valueId
    ) {
        return ApiResponse.ok(dictValueApplicationService.detail(valueId));
    }

    @SysLog("修改字典值")
    @Operation(summary = "修改字典值")
    @PutMapping("/dict-values/{valueId}")
    @SaCheckPermission(PermissionCodes.SYSDICT_EDIT)
    public ApiResponse<SystemViewModels.DictValueView> updateDictValue(
            @Parameter(description = "字典值 ID") @PathVariable Long valueId,
            @Valid @RequestBody DictValueCrudRequest request
    ) {
        return ApiResponse.ok(dictValueApplicationService.update(valueId, request));
    }

    @SysLog("删除字典值")
    @Operation(summary = "删除字典值")
    @DeleteMapping("/dict-values/{valueId}")
    @SaCheckPermission(PermissionCodes.SYSDICT_DEL)
    public ApiResponse<Void> deleteDictValue(@Parameter(description = "字典值 ID") @PathVariable Long valueId) {
        dictValueApplicationService.delete(valueId);
        return ApiResponse.ok();
    }

    @SysLog("刷新字典缓存")
    @Operation(summary = "刷新字典缓存")
    @DeleteMapping("/dicts/cache")
    @SaCheckPermission(PermissionCodes.SYSDICT_EDIT)
    public ApiResponse<String> evictDictCache() {
        return ApiResponse.ok(dictValueApplicationService.refreshCache());
    }
}
