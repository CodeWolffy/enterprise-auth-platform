package com.enterprise.auth.platform.tenant.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "租户资源覆盖更新请求")
public record UpdateTenantResourceOverridesRequest(
        @Schema(description = "覆盖项集合", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty
        List<@Valid OverrideItem> overrides
) {
    @Schema(description = "资源覆盖项")
    public record OverrideItem(
            @Schema(description = "资源 ID", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Long resourceId,
            @Schema(description = "启用覆盖") Boolean enabled,
            @Schema(description = "可见覆盖") Boolean visible,
            @Schema(description = "排序覆盖") Integer orderNo,
            @Schema(description = "标题覆盖") String titleOverride,
            @Schema(description = "图标覆盖") String iconOverride
    ) {
    }
}
