package com.enterprise.auth.platform.modules.auth.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "菜单节点")
public record MenuNode(
        @Schema(description = "资源 ID") Long id,
        @Schema(description = "菜单编码") String code,
        @Schema(description = "菜单标题") String title,
        @Schema(description = "菜单名称") String name,
        @Schema(description = "访问路径") String path,
        @Schema(description = "前端组件名") String component,
        @Schema(description = "权限标识") String permission,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序值") Integer sort,
        @Schema(description = "外链状态") boolean outerStatus,
        @Schema(description = "子节点") List<MenuNode> children
) {
    public record MenuItem(String code, String title, String path, String component) {
    }
}