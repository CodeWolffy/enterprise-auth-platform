package com.enterprise.auth.platform.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.common.observability.PlatformMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PlatformMetricsTest {

    @Test
    void shouldPublishBoundedBusinessMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PlatformMetrics metrics = new PlatformMetrics(registry);

        metrics.recordLogin("SUCCESS", null, 1_000_000);
        metrics.recordAuthorizationDenied("403", "ACCESS_DENIED");
        metrics.recordWorkflowAction("TASK_APPROVE", "SUCCESS", 2_000_000);
        metrics.recordNotificationPublish("WORKFLOW_TASK_URGE", "SUCCESS", 3);

        assertThat(registry.get("platform.auth.login.attempts")
                .tags("outcome", "success", "reason", "none").counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.security.authorization.denied")
                .tags("status", "403", "reason", "access_denied").counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.workflow.actions")
                .tags("action", "task_approve", "outcome", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("platform.notification.published")
                .tag("scenario", "workflow_task_urge").counter().count()).isEqualTo(3);
    }
}
