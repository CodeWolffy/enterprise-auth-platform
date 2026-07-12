package com.enterprise.auth.platform.modules.log.application;

import java.time.Instant;

/** 日志统计投影：最近审计事件。 */
public record LogRecentAuditEvent(
        String eventType,
        String operator,
        String tenantId,
        String clientIp,
        Instant occurredAt
) {
}