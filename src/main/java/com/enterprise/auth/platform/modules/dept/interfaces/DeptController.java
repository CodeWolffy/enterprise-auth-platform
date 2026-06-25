package com.enterprise.auth.platform.modules.dept.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.dept.interfaces.DeptCrudRequest;
import com.enterprise.auth.platform.modules.dept.application.DeptManagementService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "部门管理")
@RestController
@RequestMapping("/api/depts")
public class DeptController {

    private final CatalogService catalogService;
    private final DeptManagementService deptManagementService;

    public DeptController(CatalogService catalogService, DeptManagementService deptManagementService) {
        this.catalogService = catalogService;
        this.deptManagementService = deptManagementService;
    }

    @Operation(summary = "查询部门列表")
    @GetMapping
    @SaCheckPermission(PermissionCodes.SYSDEPT_PAGE)
    public ApiResponse<List<CatalogService.DepartmentView>> list() {
        return ApiResponse.ok(catalogService.departments());
    }

    @SysLog("新增部门")
    @Operation(summary = "新增部门")
    @PostMapping
    @SaCheckPermission(PermissionCodes.SYSDEPT_ADD)
    public ApiResponse<CatalogService.DepartmentView> create(@Valid @RequestBody DeptCrudRequest request) {
        return ApiResponse.ok(deptManagementService.create(request));
    }

    @SysLog("修改部门")
    @Operation(summary = "修改部门")
    @PutMapping("/{deptId}")
    @SaCheckPermission(PermissionCodes.SYSDEPT_EDIT)
    public ApiResponse<CatalogService.DepartmentView> update(
            @Parameter(description = "部门 ID") @PathVariable Long deptId,
            @Valid @RequestBody DeptCrudRequest request
    ) {
        return ApiResponse.ok(deptManagementService.update(deptId, request));
    }

    @SysLog("删除部门")
    @Operation(summary = "删除部门")
    @DeleteMapping("/{deptId}")
    @SaCheckPermission(PermissionCodes.SYSDEPT_DEL)
    public ApiResponse<Void> delete(@Parameter(description = "部门 ID") @PathVariable Long deptId) {
        deptManagementService.delete(deptId);
        return ApiResponse.ok();
    }
}
