package com.enterprise.auth.platform.modules.auth.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bearer token session")
public record TokenSessionResponse(
        @Schema(description = "Tenant ID") String tenantId,
        @Schema(description = "Bearer token") String token,
        @Schema(description = "Token expiration time") Long expiresAt,
        @Schema(description = "Whether current session is restricted to password change") Boolean passwordChangeRequired,
        @Schema(description = "Password change reason: FORCE_CHANGE or PASSWORD_EXPIRED") String passwordChangeReason
) {
}
