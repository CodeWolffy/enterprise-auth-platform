package com.enterprise.auth.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.notification.application.NotificationPublishCommand;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublisher;
import com.enterprise.auth.platform.modules.notification.application.NotificationScenarioPublisher;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationScenarioPublisherTest {

    private NotificationPublisher notificationPublisher;
    private UserQueryFacade userQueryFacade;
    private UserAuthenticationFacade userAuthenticationFacade;
    private NotificationScenarioPublisher publisher;
    private List<NotificationPublishCommand> publishedCommands;

    @BeforeEach
    void setUp() {
        notificationPublisher = mock(NotificationPublisher.class);
        userQueryFacade = mock(UserQueryFacade.class);
        userAuthenticationFacade = mock(UserAuthenticationFacade.class);
        publishedCommands = new ArrayList<>();
        when(notificationPublisher.publish(any())).thenAnswer(invocation -> {
            publishedCommands.add(invocation.getArgument(0, NotificationPublishCommand.class));
            return null;
        });
        publisher = new NotificationScenarioPublisher(notificationPublisher, userQueryFacade, userAuthenticationFacade);
    }

    @Test
    void workflowTaskApprovedShouldLinkToMyInstancesRoute() {
        publisher.workflowTaskApproved(new NotificationScenarioPublisher.WorkflowTaskDecisionEvent(
                "tenant-a", 1L, "测试流程", "BK-001", 10L, 100L, "审批节点", "admin", false));

        assertThat(publishedCommands).hasSize(1);
        NotificationPublishCommand command = publishedCommands.get(0);
        assertThat(command.link()).isEqualTo("/platform/workflow/my-instances");
        assertThat(command.actionPayload()).containsEntry("route", "/platform/workflow/my-instances");
    }

    @Test
    void workflowTaskRejectedShouldLinkToMyInstancesRoute() {
        publisher.workflowTaskRejected(new NotificationScenarioPublisher.WorkflowTaskDecisionEvent(
                "tenant-a", 1L, "测试流程", "BK-002", 10L, 101L, "审批节点", "admin", true));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/platform/workflow/my-instances");
    }

    @Test
    void workflowTaskTransferredShouldLinkToMyInstancesRoute() {
        publisher.workflowTaskTransferred(new NotificationScenarioPublisher.WorkflowTaskTransferEvent(
                "tenant-a", 1L, "测试流程", "BK-003", 10L, 100L, 102L, "审批节点", 20L, "user-b", "admin"));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/platform/workflow/my-instances");
    }

    @Test
    void workflowInstanceWithdrawnShouldLinkToMyInstancesRoute() {
        publisher.workflowInstanceWithdrawn(new NotificationScenarioPublisher.WorkflowInstanceClosedEvent(
                "tenant-a", 1L, "测试流程", "BK-004", Set.of(10L), Set.of(), "admin"));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/platform/workflow/my-instances");
    }

    @Test
    void workflowInstanceTerminatedShouldLinkToMyInstancesRoute() {
        publisher.workflowInstanceTerminated(new NotificationScenarioPublisher.WorkflowInstanceClosedEvent(
                "tenant-a", 1L, "测试流程", "BK-005", Set.of(10L), Set.of(), "admin"));

        assertThat(publishedCommands).hasSize(1);
        assertThat(publishedCommands.get(0).link()).isEqualTo("/platform/workflow/my-instances");
    }

    @Test
    void systemNoticePublishedShouldLinkToPublicNoticeDetailRoute() {
        when(userQueryFacade.listAllEnabledUserIds("tenant-a")).thenReturn(Set.of(10L, 11L));

        publisher.systemNoticePublished("tenant-a", 42L, "系统维护", "<p>维护公告</p>", "admin");

        assertThat(publishedCommands).hasSize(1);
        NotificationPublishCommand command = publishedCommands.get(0);
        assertThat(command.link()).isEqualTo("/notices/42");
        assertThat(command.actionPayload()).containsEntry("route", "/notices");
        assertThat(command.actionPayload()).containsEntry("noticeId", 42L);
    }
}
