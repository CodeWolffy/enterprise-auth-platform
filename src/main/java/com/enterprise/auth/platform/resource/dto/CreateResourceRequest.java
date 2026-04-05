package com.enterprise.auth.platform.resource.dto;

import com.enterprise.auth.platform.common.model.ResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "新增资源请求")
public record CreateResourceRequest(
        @Schema(description = "父节点 ID") Long parentId,
        @Schema(description = "资源类型", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull ResourceType resourceType,
        @Schema(description = "资源键", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String resourceKey,
        @Schema(description = "资源名称", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String resourceName,
        @Schema(description = "路由键") String routeKey,
        @Schema(description = "授权键") String grantKey,
        @Schema(description = "访问路径") String path,
        @Schema(description = "组件名") String component,
        @Schema(description = "图标") String icon,
        @Schema(description = "排序值") Integer orderNo,
        @Schema(description = "可见状态") Boolean visible,
        @Schema(description = "启用状态") Boolean enabled
) {
}
