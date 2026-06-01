package com.enterprise.auth.platform.modules.auth.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Duration sessionTtl,
        Duration captchaTtl,
        boolean cookieSecure,
        String cookieSameSite,
        Redis redis
) {

    public Redis resolvedRedis() {
        return redis == null ? Redis.defaults() : redis;
    }

    public record Redis(
            boolean sessionEnabled,
            boolean captchaEnabled,
            boolean redissonEnabled,
            String keyPrefix,
            String namespaceVersion
    ) {

        private static Redis defaults() {
            return new Redis(true, true, true, "eap:auth:", "v1");
        }

        public String resolvedKeyPrefix() {
            if (keyPrefix == null || keyPrefix.isBlank()) {
                return "eap:auth:";
            }
            return keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
        }

        public String resolvedNamespaceVersion() {
            if (namespaceVersion == null || namespaceVersion.isBlank()) {
                return "v1";
            }
            return namespaceVersion.trim();
        }

        public String resolvedNamespacePrefix() {
            return resolvedKeyPrefix() + resolvedNamespaceVersion() + ":";
        }
    }
}