package com.enterprise.auth.platform.modules.menu.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "菜单树节点")
public record MenuTreeNode(
        @Schema(description = "菜单 ID") Long id,
        @Schema(description = "菜单类型") String menuType,
        @Schema(description = "资源唯一标识") String resourceKey,
        @Schema(description = "菜单名称") String menuName,
        @Schema(description = "父节点 ID") Long parentId,
        @Schema(description = "祖先链") String ancestors,
        @Schema(description = "路由标识") String routeKey,
        @Schema(description = "授权键") String grantKey,
        @Schema(description = "路由路径") String path,
        @Schema(description = "组件名") String component,
        @Schema(description = "重定向路径") String redirect,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序值") Integer orderNo,
        @Schema(description = "可见状态") boolean visible,
        @Schema(description = "启用状态") boolean enabled,
        @Schema(description = "系统资源") boolean system,
        @Schema(description = "外链状态") boolean outerStatus,
        @Schema(description = "应用标识") String applicationKey,
        @Schema(description = "子节点") List<MenuTreeNode> children
) {
}