package com.enterprise.auth.platform.modules.auth.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Bearer token session")
public record TokenSessionResponse(
        @Schema(description = "Tenant ID") String tenantId,
        @Schema(description = "Bearer token") String token,
        @Schema(description = "Token expiration time, ISO-8601 UTC") Instant expiresAt,
        @Schema(description = "Whether current session is restricted to password change") Boolean passwordChangeRequired,
        @Schema(description = "Password change reason: FORCE_CHANGE or PASSWORD_EXPIRED") String passwordChangeReason
) {
}
