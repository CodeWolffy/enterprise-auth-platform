package com.enterprise.auth.platform.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Bearer token session")
public record TokenSessionResponse(
        @Schema(description = "Tenant ID") String tenantId,
        @Schema(description = "Bearer token") String token,
        @Schema(description = "Token expiration time") Long expiresAt
) {
}
