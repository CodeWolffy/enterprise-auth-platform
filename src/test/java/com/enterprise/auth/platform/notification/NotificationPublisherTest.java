package com.enterprise.auth.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.notification.application.NotificationPublishCommand;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublisher;
import com.enterprise.auth.platform.modules.notification.application.NotificationSseRegistry;
import com.enterprise.auth.platform.modules.notification.application.NotificationView;
import com.enterprise.auth.platform.common.observability.PlatformMetrics;
import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysUserNotificationMapper;
import com.enterprise.auth.platform.modules.role.application.RoleQueryFacade;
import com.enterprise.auth.platform.modules.user.application.UserQueryFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationPublisherTest {

    private SysUserNotificationMapper notificationMapper;
    private UserQueryFacade userQueryFacade;
    private NotificationSseRegistry sseRegistry;
    private NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        notificationMapper = mock(SysUserNotificationMapper.class);
        RoleQueryFacade roleQueryFacade = mock(RoleQueryFacade.class);
        userQueryFacade = mock(UserQueryFacade.class);
        sseRegistry = mock(NotificationSseRegistry.class);
        publisher = new NotificationPublisher(
                notificationMapper,
                roleQueryFacade,
                userQueryFacade,
                new ObjectMapper(),
                sseRegistry,
                mock(PlatformMetrics.class));
    }

    @Test
    void publishShouldBatchInsertOfflineRecipientsWithoutPerUserInsert() {
        Set<Long> recipients = LongStream.rangeClosed(1, 1200)
                .boxed()
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<Integer> batchSizes = new ArrayList<>();
        when(userQueryFacade.listEnabledUserIds("tenant-a", recipients)).thenReturn(recipients);
        when(notificationMapper.batchInsertIgnore(anyList())).thenAnswer(invocation -> {
            List<?> batch = invocation.getArgument(0, List.class);
            batchSizes.add(batch.size());
            return batch.size();
        });

        long published = publisher.publish(command(recipients));

        assertThat(published).isEqualTo(1200);
        assertThat(batchSizes).containsExactly(500, 500, 200);
        verify(notificationMapper, never()).insert(any(SysUserNotificationEntity.class));
        verify(sseRegistry, never()).send(any(), any(), any());
    }

    @Test
    void publishShouldInsertAndPushOnlineRecipientImmediately() {
        Set<Long> recipients = Set.of(1L, 2L);
        when(userQueryFacade.listEnabledUserIds("tenant-a", recipients)).thenReturn(recipients);
        when(sseRegistry.hasActiveConnection("tenant-a", 2L)).thenReturn(true);
        when(notificationMapper.batchInsertIgnore(anyList())).thenReturn(1);
        when(notificationMapper.insert(any(SysUserNotificationEntity.class))).thenAnswer(invocation -> {
            SysUserNotificationEntity entity = invocation.getArgument(0, SysUserNotificationEntity.class);
            entity.setId(88L);
            return 1;
        });

        long published = publisher.publish(command(recipients));

        assertThat(published).isEqualTo(2);
        verify(notificationMapper).insert(any(SysUserNotificationEntity.class));
        ArgumentCaptor<NotificationView> viewCaptor = ArgumentCaptor.forClass(NotificationView.class);
        verify(sseRegistry).send(eq("tenant-a"), eq(2L), viewCaptor.capture());
        assertThat(viewCaptor.getValue().id()).isEqualTo(88L);
    }

    private NotificationPublishCommand command(Set<Long> recipients) {
        return new NotificationPublishCommand(
                "tenant-a",
                "UNIT_TEST",
                "UNIT_TEST",
                "source-1",
                "UNIT_TEST",
                "biz-1",
                recipients,
                Set.of(),
                false,
                java.util.Map.of(),
                "测试通知",
                "测试内容",
                "INFO",
                "/unit-test",
                java.util.Map.of("route", "/unit-test"),
                java.util.Map.of("scope", "unit"),
                "UNIT_TEST:source-1",
                null,
                "tester");
    }
}
