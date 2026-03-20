package com.enterprise.auth.platform.auth.dto;

import java.time.Instant;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "在线会话响应")
public record UserSessionResponse(
        @Schema(description = "会话ID") String sessionId,
        @Schema(description = "用户名") String username,
        @Schema(description = "租户编码") String tenantId,
        @Schema(description = "客户端IP") String clientIp,
        @Schema(description = "设备标识") String device,
        @Schema(description = "签发时间") Instant issuedAt,
        @Schema(description = "过期时间") Instant expiresAt,
        @Schema(description = "最后访问时间") Instant lastAccessAt,
        @Schema(description = "是否仍然有效") boolean active
) {
}
