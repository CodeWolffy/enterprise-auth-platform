package com.enterprise.auth.platform.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int defaultCapacity,
        int defaultRefillTokens,
        Duration defaultRefillDuration,
        FailureMode failureMode,
        List<String> trustedProxies,
        Map<String, LimitRule> rules
) {

    public enum FailureMode {
        OPEN,
        CLOSED
    }

    public record LimitRule(int capacity, int refillTokens, Duration refillDuration, FailureMode failureMode) {
    }

    public int resolvedDefaultCapacity() {
        return defaultCapacity <= 0 ? 20 : defaultCapacity;
    }

    public int resolvedDefaultRefillTokens() {
        return defaultRefillTokens <= 0 ? 20 : defaultRefillTokens;
    }

    public Duration resolvedDefaultRefillDuration() {
        return defaultRefillDuration == null || defaultRefillDuration.isZero() || defaultRefillDuration.isNegative()
                ? Duration.ofMinutes(1)
                : defaultRefillDuration;
    }

    public FailureMode resolvedFailureMode() {
        return failureMode == null ? FailureMode.OPEN : failureMode;
    }

    public List<String> resolvedTrustedProxies() {
        return trustedProxies == null ? List.of() : trustedProxies.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    public LimitRule resolveRule(String key) {
        if (rules != null && rules.containsKey(key)) {
            return rules.get(key);
        }
        return null;
    }

    public FailureMode resolveFailureMode(String key) {
        LimitRule rule = resolveRule(key);
        if (rule != null && rule.failureMode() != null) {
            return rule.failureMode();
        }
        return resolvedFailureMode();
    }
}
