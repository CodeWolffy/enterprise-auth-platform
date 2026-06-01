package com.enterprise.auth.platform.modules.resource.domain;

import com.enterprise.auth.platform.modules.resource.domain.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "资源树节点")
public record ResourceTreeNode(
        @Schema(description = "资源 ID") Long id,
        @Schema(description = "资源键") String resourceKey,
        @Schema(description = "资源名称") String resourceName,
        @Schema(description = "资源类型") ResourceType resourceType,
        @Schema(description = "父节点 ID") Long parentId,
        @Schema(description = "祖先链") String ancestors,
        @Schema(description = "路由键") String routeKey,
        @Schema(description = "授权键") String grantKey,
        @Schema(description = "访问路径") String path,
        @Schema(description = "组件名") String component,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序值") Integer orderNo,
        @Schema(description = "可见状态") boolean visible,
        @Schema(description = "启用状态") boolean enabled,
        @Schema(description = "系统资源") boolean system,
        @Schema(description = "子节点") List<ResourceTreeNode> children
) {
}
