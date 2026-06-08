package com.enterprise.auth.platform.modules.role.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

@Schema(description = "角色菜单分配请求")
public record AssignMenusRequest(
        @Schema(description = "菜单/权限节点 ID 集合", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Set<Long> menuIds
) {
}
