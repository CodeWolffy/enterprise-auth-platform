package com.enterprise.auth.platform.modules.system.interfaces;

import java.time.Instant;

public record MailChannelResponse(
        Long id,
        String tenantId,
        String provider,
        String mailHost,
        Integer mailPort,
        String mailUsername,
        String mailFrom,
        String mailProtocol,
        boolean useSsl,
        boolean useStartTls,
        boolean enabled,
        boolean passwordConfigured,
        boolean inherited,
        String sourceTenantId,
        Instant createdAt,
        Instant updatedAt
) {
}