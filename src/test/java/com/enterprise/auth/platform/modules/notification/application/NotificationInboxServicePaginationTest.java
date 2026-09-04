package com.enterprise.auth.platform.modules.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysNoticeReadStatusMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.mapper.SysUserNotificationMapper;
import com.enterprise.auth.platform.modules.notification.infrastructure.projection.NotificationInboxProjection;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationInboxServicePaginationTest {

    private static final String TENANT_ID = "tenant-a";
    private static final Long USER_ID = 9L;

    private final SysUserNotificationMapper notificationMapper = mock(SysUserNotificationMapper.class);
    private final SysNoticeReadStatusMapper noticeReadStatusMapper = mock(SysNoticeReadStatusMapper.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final NotificationInboxService service = new NotificationInboxService(
            notificationMapper, noticeReadStatusMapper, currentUserService);
    private final UserAccount user = new UserAccount(
            USER_ID,
            TENANT_ID,
            "notification-user",
            "hash",
            true,
            Set.of(),
            Set.of(),
            Set.of(),
            DataScopeType.ALL,
            1);

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        when(currentUserService.requireCurrentUser()).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldCountAndPageUnifiedInboxInDatabaseForDeepPages() {
        Instant createdAt = Instant.parse("2026-09-04T10:00:00Z");
        NotificationInboxProjection projection = direct(601L, createdAt);
        when(notificationMapper.countVisibleNotifications(eq(TENANT_ID), eq(USER_ID), eq(null), any(Instant.class)))
                .thenReturn(1201L);
        when(notificationMapper.listVisibleNotifications(
                eq(TENANT_ID), eq(USER_ID), eq(null), any(Instant.class), eq(600), eq(100)))
                .thenReturn(List.of(projection));

        var result = service.myNotifications(7, 100, null);

        assertThat(result.total()).isEqualTo(1201L);
        assertThat(result.page()).isEqualTo(7);
        assertThat(result.size()).isEqualTo(100);
        assertThat(result.records()).extracting(NotificationView::id).containsExactly(601L);
        verify(notificationMapper).countVisibleNotifications(eq(TENANT_ID), eq(USER_ID), eq(null), any(Instant.class));
        verify(notificationMapper).listVisibleNotifications(
                eq(TENANT_ID), eq(USER_ID), eq(null), any(Instant.class), eq(600), eq(100));
        verify(notificationMapper, never()).selectCount(any());
        verify(noticeReadStatusMapper, never()).countVisibleBroadcasts(any(), any(), any(), any());
        verify(noticeReadStatusMapper, never()).listVisibleBroadcasts(any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldPreserveBroadcastProjectionResponseSemantics() {
        Instant publishedAt = Instant.parse("2026-09-04T11:00:00Z");
        NotificationInboxProjection projection = new NotificationInboxProjection();
        projection.setId(-42L);
        projection.setBroadcastFlag(1);
        projection.setNoticeTitle("维护公告");
        projection.setNoticeContent("<p>维护内容</p>");
        projection.setPublishTime(publishedAt);
        projection.setCreatedAt(publishedAt);
        when(notificationMapper.countVisibleNotifications(eq(TENANT_ID), eq(USER_ID), eq(false), any(Instant.class)))
                .thenReturn(1L);
        when(notificationMapper.listVisibleNotifications(
                eq(TENANT_ID), eq(USER_ID), eq(false), any(Instant.class), eq(0), eq(20)))
                .thenReturn(List.of(projection));

        var result = service.myNotifications(1, 20, false);

        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(notification -> {
            assertThat(notification.id()).isEqualTo(-42L);
            assertThat(notification.scenarioCode()).isEqualTo("SYSTEM_NOTICE_PUBLISHED");
            assertThat(notification.title()).isEqualTo("系统公告：维护公告");
            assertThat(notification.content()).isEqualTo("维护内容");
            assertThat(notification.read()).isFalse();
            assertThat(notification.createdAt()).isEqualTo(publishedAt);
        });
    }

    @Test
    void shouldKeepPageSizeHardLimitAndAvoidIntegerOffsetOverflow() {
        when(notificationMapper.countVisibleNotifications(eq(TENANT_ID), eq(USER_ID), eq(true), any(Instant.class)))
                .thenReturn(0L);
        when(notificationMapper.listVisibleNotifications(
                eq(TENANT_ID), eq(USER_ID), eq(true), any(Instant.class), eq(Integer.MAX_VALUE), eq(100)))
                .thenReturn(List.of());

        var result = service.myNotifications(Integer.MAX_VALUE, Integer.MAX_VALUE, true);

        assertThat(result.page()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.size()).isEqualTo(100);
        verify(notificationMapper).listVisibleNotifications(
                eq(TENANT_ID), eq(USER_ID), eq(true), any(Instant.class), eq(Integer.MAX_VALUE), eq(100));
    }

    private NotificationInboxProjection direct(Long id, Instant createdAt) {
        NotificationInboxProjection projection = new NotificationInboxProjection();
        projection.setId(id);
        projection.setBroadcastFlag(0);
        projection.setScenarioCode("UNIT_TEST");
        projection.setSourceType("UNIT_TEST");
        projection.setSourceId("source-1");
        projection.setBizType("UNIT_TEST");
        projection.setBizId("biz-1");
        projection.setTitle("直接通知");
        projection.setContent("通知内容");
        projection.setLevel("INFO");
        projection.setLink("/unit-test");
        projection.setReadAt(null);
        projection.setExpiresAt(null);
        projection.setCreatedAt(createdAt);
        return projection;
    }
}
