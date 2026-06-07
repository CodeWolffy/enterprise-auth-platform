package com.enterprise.auth.platform.modules.role.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "角色菜单分配请求")
public record AssignMenusRequest(
        @Schema(description = "菜单/权限节点 ID 集合") Set<Long> menuIds,
        @Schema(description = "兼容旧资源 ID 字段，等价于 menuIds") Set<Long> resourceIds
) {
    public Set<Long> resolvedMenuIds() {
        return menuIds != null ? menuIds : resourceIds;
    }
}