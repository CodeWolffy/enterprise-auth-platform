package com.enterprise.auth.platform.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(
        String defaultTenantId,
    List<String> defaultRoleCodes,
        boolean rateLimitEnabled,
        long maxAttemptsPerUserIp,
        long maxAttemptsPerIp,
        Duration attemptWindow
) {

    public String resolvedDefaultTenantId() {
        if (defaultTenantId == null || defaultTenantId.isBlank()) {
            return "tenant-a";
        }
        return defaultTenantId.trim();
    }

    public long resolvedMaxAttemptsPerUserIp() {
        return maxAttemptsPerUserIp <= 0 ? 10 : maxAttemptsPerUserIp;
    }

    public long resolvedMaxAttemptsPerIp() {
        return maxAttemptsPerIp <= 0 ? 40 : maxAttemptsPerIp;
    }

    public Set<String> resolvedDefaultRoleCodes() {
        if (defaultRoleCodes == null || defaultRoleCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (String item : defaultRoleCodes) {
            if (!StringUtils.hasText(item)) {
                continue;
            }
            String[] parts = item.split(",");
            for (String part : parts) {
                if (StringUtils.hasText(part)) {
                    codes.add(part.trim());
                }
            }
        }
        return codes.isEmpty() ? Set.of() : Set.copyOf(codes);
    }

    public Duration resolvedAttemptWindow() {
        return attemptWindow == null || attemptWindow.isZero() || attemptWindow.isNegative()
                ? Duration.ofMinutes(10)
                : attemptWindow;
    }
}