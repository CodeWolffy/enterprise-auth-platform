package com.enterprise.auth.platform.modules.auth.domain;

public record SessionPrincipal(
        String sessionId,
        String tenantId,
        String operatorTenantId,
        boolean globalScope
) {
}
