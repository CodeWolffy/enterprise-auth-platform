package com.enterprise.auth.platform.modules.user.interfaces;

import java.time.Instant;

public record AccountProfileResponse(
        Long id,
        String tenantId,
        String username,
        String displayName,
        String mobile,
        String email,
        String avatarFileKey,
        String avatarUrl,
        boolean enabled,
        boolean mustChangePassword,
        Instant passwordUpdatedAt,
        Instant lastLoginAt,
        String lastLoginIp,
        Instant createdAt,
        Instant updatedAt
) {
}