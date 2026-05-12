package com.enterprise.auth.platform.dto.model;

public record SessionPrincipal(
        String sessionId,
        String tenantId,
        String operatorTenantId
) {
}
