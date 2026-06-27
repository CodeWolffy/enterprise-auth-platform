package com.enterprise.auth.platform.modules.tenant.interfaces;

import com.enterprise.auth.platform.common.authz.PermissionCodes;
import com.enterprise.auth.platform.modules.log.infrastructure.annotation.SysLog;
import com.enterprise.auth.platform.modules.tenant.application.TenantCatalogApplicationService;
import com.enterprise.auth.platform.common.web.ApiResponse;
import com.enterprise.auth.platform.modules.tenant.interfaces.TenantPackageCrudRequest;
import com.enterprise.auth.platform.modules.tenant.application.TenantCatalogManagementService;
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

@Tag(name = "租户目录管理")
@RestController
@RequestMapping("/api/tenant-catalog")
public class TenantCatalogController {

    private final TenantCatalogApplicationService tenantCatalogApplicationService;

    public TenantCatalogController(TenantCatalogApplicationService tenantCatalogApplicationService) {
        this.tenantCatalogApplicationService = tenantCatalogApplicationService;
    }

    @Operation(summary = "查询套餐列表")
    @GetMapping("/packages")
    @SaCheckPermission(PermissionCodes.TENANT_PACKAGE_PAGE)
    public ApiResponse<List<TenantCatalogManagementService.TenantPackageView>> packages() {
        return ApiResponse.ok(tenantCatalogApplicationService.packages());
    }

    @SysLog("新增套餐")
    @Operation(summary = "新增套餐")
    @PostMapping("/packages")
    @SaCheckPermission(PermissionCodes.TENANT_PACKAGE_ADD)
    public ApiResponse<TenantCatalogManagementService.TenantPackageView> createPackage(
            @Valid @RequestBody TenantPackageCrudRequest request
    ) {
        return ApiResponse.ok(tenantCatalogApplicationService.createPackage(request));
    }

    @SysLog("修改套餐")
    @Operation(summary = "修改套餐")
    @PutMapping("/packages/{id}")
    @SaCheckPermission(PermissionCodes.TENANT_PACKAGE_EDIT)
    public ApiResponse<TenantCatalogManagementService.TenantPackageView> updatePackage(
            @Parameter(description = "套餐主键 ID") @PathVariable Long id,
            @Valid @RequestBody TenantPackageCrudRequest request
    ) {
        return ApiResponse.ok(tenantCatalogApplicationService.updatePackage(id, request));
    }

    @Operation(summary = "套餐变更影响分析")
    @GetMapping("/packages/{id}/impact")
    @SaCheckPermission(PermissionCodes.TENANT_PACKAGE_GET)
    public ApiResponse<TenantCatalogManagementService.TenantPackageImpactView> packageImpact(
            @Parameter(description = "套餐主键 ID") @PathVariable Long id
    ) {
        return ApiResponse.ok(tenantCatalogApplicationService.packageImpact(id));
    }

    @SysLog("删除套餐")
    @Operation(summary = "删除套餐")
    @DeleteMapping("/packages/{id}")
    @SaCheckPermission(PermissionCodes.TENANT_PACKAGE_DEL)
    public ApiResponse<Void> deletePackage(@Parameter(description = "套餐主键 ID") @PathVariable Long id) {
        tenantCatalogApplicationService.deletePackage(id);
        return ApiResponse.ok();
    }

}
