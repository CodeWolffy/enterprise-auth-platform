package com.enterprise.auth.platform.auth.dto;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.common.model.MenuItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

@Schema(description = "当前用户权限快照")
public record PermissionSnapshotResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "当前操作租户编码") String tenantId,
        @Schema(description = "操作者所属租户编码") String operatorTenantId,
        @Schema(description = "角色编码集合") Set<String> roles,
        @Schema(description = "权限编码集合") Set<String> permissions,
        @Schema(description = "数据权限范围") DataScopeType dataScopeType,
        @Schema(description = "自定义部门 ID 集合") Set<Long> customDeptIds,
        @Schema(description = "当前可见菜单集合") List<MenuItem> menus,
        @Schema(description = "是否平台超级管理员") boolean superAdmin
) {
}
