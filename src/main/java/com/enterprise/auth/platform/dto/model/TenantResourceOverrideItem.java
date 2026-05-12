package com.enterprise.auth.platform.dto.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "租户资源覆盖项")
public record TenantResourceOverrideItem(
        @Schema(description = "资源 ID") Long resourceId,
        @Schema(description = "资源键") String resourceKey,
        @Schema(description = "资源名称") String resourceName,
        @Schema(description = "启用覆盖") Boolean enabled,
        @Schema(description = "可见覆盖") Boolean visible,
        @Schema(description = "排序覆盖") Integer orderNo,
        @Schema(description = "标题覆盖") String titleOverride,
        @Schema(description = "图标覆盖") String iconOverride
) {
}
