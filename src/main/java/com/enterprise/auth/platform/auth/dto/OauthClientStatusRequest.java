package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "OAuth2 客户端状态变更请求")
public record OauthClientStatusRequest(
        @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
