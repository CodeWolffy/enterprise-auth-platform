package com.enterprise.auth.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.notification.NotificationScenarioPort;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublishCommand;
import com.enterprise.auth.platform.modules.notification.application.NotificationScenarioPublisher;
import com.enterprise.auth.platform.modules.notification.application.NotificationSseRegistry;
import com.enterprise.auth.platform.modules.system.application.OutboxDispatchWorker;
import com.enterprise.auth.platform.modules.system.application.OutboxWriter;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationScenarioPublisherTest {

    private NotificationSseRegistry sseRegistry;
    private UserAuthenticationFacade userAuthenticationFacade;
    private OutboxWriter outboxWriter;
    private NotificationScenarioPublisher publisher;
    private List<NotificationPublishCommand> publishedCommands;

    @BeforeEach
    void setUp() {
        sseRegistry = mock(NotificationSseRegistry.class);
        userAuthenticationFacade = mock(UserAuthenticationFacade.class);
        outboxWriter = mock(OutboxWriter.class);
        OutboxDispatchWorker outboxDispatchWorker = mock(OutboxDispatchWorker.class);
        publishedCommands = new ArrayList<>();
        when(outboxWriter.enqueue(any(), any(), any(), any(), any())).thenAnswer(invocation -> {
            Object payload = invocation.getArgument(4);
            if (payload instanceof NotificationPublishCommand command) {
                publishedCommands.add(command);
            }
            return 1L;
        });
        publisher = new NotificationScenarioPublisher(
                sseRegistry,
                userAuthenticationFacade,
                outboxWriter,
                outboxDispatchWorker
        );
    }

    @Test
    void workflowTaskApprovedShouldLinkToMyInstancesRoute() {
        publisher.workflowTaskApproved(new NotificationScenarioPort.WorkflowTaskDecisionEvent(
                "tenant-a", 1L, "测试流程", "BK-001", 10L, 100L, "审批节点", "admin", false));

        assertThat(publishedCommands).hasSize(1);
        NotificationPublishCommand command = publishedCommands.get(0);
        assertThat(command.link()).isEqualTo("/workflow/instances");
        assertThat(command.actionPayload()).containsEntry("route", "/workflow/instances");
    }

    @Test
    void workflowTaskRejectedShouldLinkToMyInstancesRoute() {
        publisher.workflowTaskRejected(new NotificationScenarioPort.WorkflowTaskDecisionEvent(
                "tenant-a", 1L, "测试流程", "BK-002", 10L, 101L, "审批节点", "admin", true));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/workflow/instances");
    }

    @Test
    void workflowTaskTransferredShouldLinkToMyInstancesRoute() {
        publisher.workflowTaskTransferred(new NotificationScenarioPort.WorkflowTaskTransferEvent(
                "tenant-a", 1L, "测试流程", "BK-003", 10L, 100L, 102L, "审批节点", 20L, "user-b", "admin"));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/workflow/instances");
    }

    @Test
    void workflowInstanceWithdrawnShouldLinkToMyInstancesRoute() {
        publisher.workflowInstanceWithdrawn(new NotificationScenarioPort.WorkflowInstanceClosedEvent(
                "tenant-a", 1L, "测试流程", "BK-004", Set.of(10L), Set.of(), "admin"));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/workflow/instances");
    }

    @Test
    void workflowInstanceTerminatedShouldLinkToMyInstancesRoute() {
        publisher.workflowInstanceTerminated(new NotificationScenarioPort.WorkflowInstanceClosedEvent(
                "tenant-a", 1L, "测试流程", "BK-005", Set.of(10L), Set.of(), "admin"));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/workflow/instances");
    }

    @Test
    void systemNoticePublishedShouldBroadcastToActiveTenantConnectionsOnly() {
        publisher.systemNoticePublished("tenant-a", 42L, "系统维护", "<p>维护公告</p>", "admin");

        assertThat(publishedCommands).isEmpty();
        verify(sseRegistry).sendTenant(eq("tenant-a"), any());
    }
}