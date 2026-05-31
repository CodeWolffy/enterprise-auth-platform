package com.enterprise.auth.platform.common.audit;

import java.util.Map;

public record PlatformAuditEvent(
        String type,
        String operator,
        String tenantId,
        Map<String, Object> details
) {
    public PlatformAuditEvent {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static PlatformAuditEvent of(String type, String operator, String tenantId, Map<String, Object> details) {
        return new PlatformAuditEvent(type, operator, tenantId, details);
    }
}