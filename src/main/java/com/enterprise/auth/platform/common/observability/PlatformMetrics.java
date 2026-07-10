package com.enterprise.auth.platform.common.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PlatformMetrics {

    private static final int MAX_TAG_LENGTH = 48;

    private final MeterRegistry registry;

    public PlatformMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordLogin(String outcome, String reason, long durationNanos) {
        String safeOutcome = tag(outcome, "unknown");
        String safeReason = tag(reason, "none");
        registry.counter("platform.auth.login.attempts", "outcome", safeOutcome, "reason", safeReason).increment();
        registry.timer("platform.auth.login.duration", "outcome", safeOutcome)
                .record(Duration.ofNanos(Math.max(durationNanos, 0L)));
    }

    public void recordAuthorizationDenied(String status, String reason) {
        registry.counter(
                "platform.security.authorization.denied",
                "status", tag(status, "unknown"),
                "reason", tag(reason, "unknown")
        ).increment();
    }

    public void recordWorkflowAction(String action, String outcome, long durationNanos) {
        String safeAction = tag(action, "unknown");
        String safeOutcome = tag(outcome, "unknown");
        registry.counter("platform.workflow.actions", "action", safeAction, "outcome", safeOutcome).increment();
        registry.timer("platform.workflow.action.duration", "action", safeAction, "outcome", safeOutcome)
                .record(Duration.ofNanos(Math.max(durationNanos, 0L)));
    }

    public void recordNotificationPublish(String scenario, String outcome, long published) {
        String safeScenario = tag(scenario, "unspecified");
        String safeOutcome = tag(outcome, "unknown");
        registry.counter("platform.notification.publish.attempts", "scenario", safeScenario, "outcome", safeOutcome)
                .increment();
        if (published > 0) {
            registry.counter("platform.notification.published", "scenario", safeScenario).increment(published);
        }
    }

    private String tag(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]", "_");
        return normalized.length() <= MAX_TAG_LENGTH ? normalized : normalized.substring(0, MAX_TAG_LENGTH);
    }
}
