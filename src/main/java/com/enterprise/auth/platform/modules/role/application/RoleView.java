package com.enterprise.auth.platform.modules.role.application;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "角色目录项")
public record RoleView(
        @Schema(description = "角色 ID") Long id,
        @Schema(description = "租户编码") String tenantId,
        @Schema(description = "角色编码") String code,
        @Schema(description = "角色名称") String name,
        @Schema(description = "角色描述") String description,
        @Schema(description = "数据权限范围") DataScopeType dataScopeType,
        @Schema(description = "自定义部门 ID 集合") List<Long> customDeptIds
) {
}
