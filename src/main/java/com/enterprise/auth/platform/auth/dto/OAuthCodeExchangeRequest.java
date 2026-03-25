package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "OAuth 授权码交换请求")
public record OAuthCodeExchangeRequest(
        @Schema(description = "授权码", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String code,
        @Schema(description = "PKCE 代码验证器", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String codeVerifier,
        @Schema(description = "回调地址", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String redirectUri
) {
}
