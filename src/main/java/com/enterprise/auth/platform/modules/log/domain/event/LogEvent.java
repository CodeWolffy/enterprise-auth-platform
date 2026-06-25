package com.enterprise.auth.platform.modules.log.domain.event;

import java.util.Map;

public record LogEvent(
        String type,
        String operator,
        String tenantId,
        Map<String, Object> details,
        String requestId,
        String clientIp,
        String location,
        String method,
        String requestUri,
        String requestParams,
        Long requestTime,
        String status,
        String exMsg
) {
    public LogEvent {
        if (details == null) details = Map.of();
    }

    public static LogEvent of(String type, String operator, String tenantId, Map<String, Object> details) {
        return new LogEvent(type, operator, tenantId, details, null, null, null, null, null, null, null, null, null);
    }
}