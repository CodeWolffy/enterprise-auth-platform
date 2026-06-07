package com.enterprise.auth.platform.modules.menu.interfaces;

import com.enterprise.auth.platform.modules.menu.domain.MenuType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "新增菜单请求")
public record CreateMenuRequest(
        @Schema(description = "父节点 ID") Long parentId,
        @Schema(description = "菜单类型", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull MenuType menuType,
        @Schema(description = "资源唯一标识", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String resourceKey,
        @Schema(description = "菜单名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String menuName,
        @Schema(description = "路由标识") String routeKey,
        @Schema(description = "授权键") String grantKey,
        @Schema(description = "路由路径") String path,
        @Schema(description = "组件名") String component,
        @Schema(description = "重定向路径") String redirect,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序值") Integer orderNo,
        @Schema(description = "可见状态") Boolean visible,
        @Schema(description = "启用状态") Boolean enabled,
        @Schema(description = "外链状态") Boolean outerStatus,
        @Schema(description = "应用标识") String applicationKey
) {
}