package com.enterprise.auth.platform.config.feature;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.registration")
public record RegistrationProperties(
        boolean rateLimitEnabled,
        long maxAttemptsPerUserIp,
        long maxAttemptsPerIp,
        Duration attemptWindow
) {

    public long resolvedMaxAttemptsPerUserIp() {
        return maxAttemptsPerUserIp <= 0 ? 10 : maxAttemptsPerUserIp;
    }

    public long resolvedMaxAttemptsPerIp() {
        return maxAttemptsPerIp <= 0 ? 40 : maxAttemptsPerIp;
    }

    public Duration resolvedAttemptWindow() {
        return attemptWindow == null || attemptWindow.isZero() || attemptWindow.isNegative()
                ? Duration.ofMinutes(10)
                : attemptWindow;
    }
}