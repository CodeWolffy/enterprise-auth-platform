package com.enterprise.auth.platform.common.audit;

import java.util.Map;

public interface AuditEventPublisher {

    void publish(PlatformAuditEvent event);

    default void publish(String type, String operator, String tenantId, Map<String, Object> details) {
        publish(PlatformAuditEvent.of(type, operator, tenantId, details));
    }
}