package com.enterprise.auth.platform.auth.model;

public record SessionPrincipal(
        String sessionId,
        String tenantId,
        String operatorTenantId
) {
}
