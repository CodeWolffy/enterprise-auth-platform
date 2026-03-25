package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "令牌响应")
public record TokenResponse(
        @Schema(description = "访问令牌") String accessToken,
        @Schema(description = "刷新令牌") String refreshToken,
        @Schema(description = "令牌类型") String tokenType,
        @Schema(description = "访问令牌过期时间") Long expiresAt,
        @Schema(description = "会话ID") String sessionId
) {
}
