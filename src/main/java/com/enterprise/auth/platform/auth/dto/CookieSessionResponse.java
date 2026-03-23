package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Cookie 会话状态")
public record CookieSessionResponse(
        @Schema(description = "租户 ID") String tenantId,
        @Schema(description = "会话 ID") String sessionId,
        @Schema(description = "访问令牌过期时间") Instant expiresAt
) {
}
