package com.enterprise.auth.platform.modules.system.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        Long pollIntervalMs,
        String payloadSecretKey
) {

    public String resolvedPayloadSecretKey() {
        return StringUtils.hasText(payloadSecretKey) ? payloadSecretKey.trim() : "";
    }
}
