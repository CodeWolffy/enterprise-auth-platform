package com.enterprise.auth.platform.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int defaultCapacity,
        int defaultRefillTokens,
        Duration defaultRefillDuration,
        Map<String, LimitRule> rules
) {

    public record LimitRule(int capacity, int refillTokens, Duration refillDuration) {
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

    public LimitRule resolveRule(String key) {
        if (rules != null && rules.containsKey(key)) {
            return rules.get(key);
        }
        return null;
    }
}
