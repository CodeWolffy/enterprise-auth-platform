package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "OAuth2 客户端密钥轮换请求")
public record RotateOauthClientSecretRequest(
        @Schema(description = "新的客户端密钥", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "新的客户端密钥不能为空")
        String clientSecret
) {
}
