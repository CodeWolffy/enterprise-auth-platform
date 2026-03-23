package com.enterprise.auth.platform.auth.model;

import java.time.Duration;
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
        Instant newExpiresAt = expiresAt;
        Duration totalLifespan = Duration.between(issuedAt, expiresAt);
        Duration remaining = Duration.between(now, expiresAt);
        if (!remaining.isNegative() && remaining.compareTo(totalLifespan.dividedBy(2)) < 0) {
            newExpiresAt = now.plus(totalLifespan);
        }
        return new UserSession(sessionId, userId, username, tenantId, clientIp, device, issuedAt, newExpiresAt, now, active);
    }

    public UserSession deactivate(Instant now) {
        return new UserSession(sessionId, userId, username, tenantId, clientIp, device, issuedAt, expiresAt, now, false);
    }
}

