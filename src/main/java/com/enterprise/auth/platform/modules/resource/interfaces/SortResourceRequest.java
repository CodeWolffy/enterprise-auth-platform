package com.enterprise.auth.platform.modules.resource.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "资源排序请求")
public record SortResourceRequest(
        @Schema(description = "排序值", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Integer orderNo
) {
}
