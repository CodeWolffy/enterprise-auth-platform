package com.enterprise.auth.platform.modules.log.application;

import java.time.Instant;

/** 操作日志列表视图（interfaces 不暴露 Entity）。 */
public record OperationLogView(
        Long id,
        String tenantId,
        String eventType,
        String operator,
        String requestId,
        String clientIp,
        String location,
        String method,
        String requestUri,
        String status,
        Long requestTime,
        Instant createdAt
) {
}