package com.enterprise.auth.platform.modules.audit.interfaces;

import com.enterprise.auth.platform.common.TimeSupport;
import java.time.Duration;
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
    public static final int MAX_PAGE_SIZE = 200;
    public static final int EXPORT_PAGE_SIZE = 2000;
    public static final long MAX_QUERY_RANGE_MS = Duration.ofDays(31).toMillis();
    private static final long DEFAULT_TO_GRACE_MS = Duration.ofMinutes(1).toMillis();

    public AuditQuery withDefaultTimeRange(long nowEpochMs) {
        long resolvedTo = toEpochMs == null ? nowEpochMs + DEFAULT_TO_GRACE_MS : toEpochMs;
        long resolvedFrom = fromEpochMs == null ? resolvedTo - MAX_QUERY_RANGE_MS : fromEpochMs;
        return new AuditQuery(
                tenantId,
                eventType,
                operator,
                requestId,
                clientIp,
                resolvedFrom,
                resolvedTo,
                page,
                size
        );
    }

    public int normalizedPage() {
        return Math.max(page, 1);
    }

    public int normalizedSize() {
        return normalizedSize(MAX_PAGE_SIZE);
    }

    public int normalizedExportSize() {
        return normalizedSize(EXPORT_PAGE_SIZE);
    }

    private int normalizedSize(int maxSize) {
        return Math.min(Math.max(size, 1), maxSize);
    }

    public Instant fromInstant() {
        return TimeSupport.fromEpochMilli(fromEpochMs);
    }

    public Instant toInstant() {
        return TimeSupport.fromEpochMilli(toEpochMs);
    }
}
