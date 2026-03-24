package com.enterprise.auth.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cache")
public record AppCacheProperties(
        String keyPrefix,
        String namespaceVersion,
        Duration defaultTtl,
        Duration authPrincipalTtl,
        Duration systemDictsTtl,
        Duration systemConfigsTtl,
        Duration systemCategoriesAllTtl,
        Duration systemCategoriesTargetTtl
) {

    public String resolvedKeyPrefix() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            return "eap:cache:";
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

    public Duration resolvedDefaultTtl() {
        return defaultTtl == null || defaultTtl.isNegative() || defaultTtl.isZero()
                ? Duration.ofMinutes(10)
                : defaultTtl;
    }

    public Duration resolvedAuthPrincipalTtl() {
        return resolvePositiveTtl(authPrincipalTtl, Duration.ofMinutes(15));
    }

    public Duration resolvedSystemDictsTtl() {
        return resolvePositiveTtl(systemDictsTtl, Duration.ofMinutes(30));
    }

    public Duration resolvedSystemConfigsTtl() {
        return resolvePositiveTtl(systemConfigsTtl, Duration.ofMinutes(30));
    }

    public Duration resolvedSystemCategoriesAllTtl() {
        return resolvePositiveTtl(systemCategoriesAllTtl, Duration.ofMinutes(60));
    }

    public Duration resolvedSystemCategoriesTargetTtl() {
        return resolvePositiveTtl(systemCategoriesTargetTtl, Duration.ofMinutes(60));
    }

    private Duration resolvePositiveTtl(Duration source, Duration fallback) {
        if (source == null || source.isNegative() || source.isZero()) {
            return fallback;
        }
        return source;
    }
}
