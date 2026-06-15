package com.enterprise.auth.platform.modules.menu.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "菜单树节点")
public record MenuTreeNode(
        @Schema(description = "菜单 ID") Long id,
        @Schema(description = "菜单类型") String type,
        @Schema(description = "菜单名称") String name,
        @Schema(description = "父节点 ID") Long parentId,
        @Schema(description = "权限标识") String permission,
        @Schema(description = "路由路径") String path,
        @Schema(description = "组件名") String component,
        @Schema(description = "重定向路径") String redirect,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序值") Integer sort,
        @Schema(description = "外链状态") boolean outerStatus,
        @Schema(description = "应用标识") String applicationKey,
        @Schema(description = "子节点") List<MenuTreeNode> children
) {
}