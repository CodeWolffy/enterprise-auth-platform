package com.enterprise.auth.platform.modules.menu.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "排序请求")
public record SortMenuRequest(
        @Schema(description = "排序值", requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Integer orderNo
) {
}