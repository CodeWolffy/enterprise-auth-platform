package com.enterprise.auth.platform.audit.model;

import java.time.Instant;

public record AuditQuery(
        String tenantId,
        String eventType,
        String operator,
        String requestId,
        String clientIp,
        Instant occurredFrom,
        Instant occurredTo,
        int page,
        int size
) {
    public int normalizedPage() {
        return Math.max(page, 1);
    }

    public int normalizedSize() {
        return Math.min(Math.max(size, 1), 100);
    }
}
