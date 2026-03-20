package com.enterprise.auth.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.redis")
public record SecurityRedisProperties(
        boolean sessionEnabled,
        boolean captchaEnabled,
    boolean redissonEnabled,
        String keyPrefix
) {

    public String resolvedKeyPrefix() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "eap:auth:";
        }
        return keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
    }
}
