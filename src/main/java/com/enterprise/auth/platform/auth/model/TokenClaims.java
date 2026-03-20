package com.enterprise.auth.platform.auth.model;

public record TokenClaims(
        String tokenId,
        String sessionId,
        Long userId,
        String username,
        String tenantId,
        String tokenType,
        int sessionVersion
) {
}

