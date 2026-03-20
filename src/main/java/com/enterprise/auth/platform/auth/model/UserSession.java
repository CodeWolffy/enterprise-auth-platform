package com.enterprise.auth.platform.auth.model;

import java.time.Instant;

public record UserSession(
        String sessionId,
        Long userId,
        String username,
        String tenantId,
        String clientIp,
        String device,
        Instant issuedAt,
        Instant expiresAt,
        Instant lastAccessAt,
        boolean active
) {

    public UserSession touch(Instant now) {
        return new UserSession(sessionId, userId, username, tenantId, clientIp, device, issuedAt, expiresAt, now, active);
    }

    public UserSession deactivate(Instant now) {
        return new UserSession(sessionId, userId, username, tenantId, clientIp, device, issuedAt, expiresAt, now, false);
    }
}

