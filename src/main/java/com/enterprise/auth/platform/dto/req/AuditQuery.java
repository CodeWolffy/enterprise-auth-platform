package com.enterprise.auth.platform.dto.req;

import com.enterprise.auth.platform.common.TimeSupport;
import java.time.Instant;

public record AuditQuery(
        String tenantId,
        String eventType,
        String operator,
        String requestId,
        String clientIp,
        Long fromEpochMs,
        Long toEpochMs,
        int page,
        int size
) {
    public int normalizedPage() {
        return Math.max(page, 1);
    }

    public int normalizedSize() {
        return Math.min(Math.max(size, 1), 100);
    }

    public Instant fromInstant() {
        return TimeSupport.fromEpochMilli(fromEpochMs);
    }

    public Instant toInstant() {
        return TimeSupport.fromEpochMilli(toEpochMs);
    }
}
