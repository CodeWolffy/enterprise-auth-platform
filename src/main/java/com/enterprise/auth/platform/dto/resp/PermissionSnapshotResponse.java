package com.enterprise.auth.platform.dto.resp;

import com.enterprise.auth.platform.dto.model.DataScopeType;
import com.enterprise.auth.platform.dto.resp.MenuNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

@Schema(description = "当前用户授权快照")
public record PermissionSnapshotResponse(
        @Schema(description = "用户 ID") Long userId,
        @Schema(description = "用户名") String username,
        @Schema(description = "当前租户 ID") String tenantId,
        @Schema(description = "操作员租户 ID") String operatorTenantId,
        @Schema(description = "角色编码集合") Set<String> roles,
        @Schema(description = "统一授权键集合") Set<String> grants,
        @Schema(description = "数据权限范围") DataScopeType dataScopeType,
        @Schema(description = "自定义部门 ID 集合") Set<Long> customDeptIds,
        @Schema(description = "可见菜单树") List<MenuNode> menus,
        @Schema(description = "是否为平台超级管理员") boolean superAdmin
) {
}