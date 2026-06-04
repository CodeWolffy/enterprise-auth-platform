package com.enterprise.auth.platform.modules.user.interfaces;

import java.time.LocalDateTime;

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
        LocalDateTime passwordUpdatedAt,
        LocalDateTime lastLoginAt,
        String lastLoginIp,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}