package com.enterprise.auth.platform.notification;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    private static final String TENANT_A = "tenant-a";
    private static final String PLATFORM = "platform";
    private static final Long USER_A = 2L;
    private static final Long USER_B = 1L;
    private static final String DEDUP_PREFIX = "notification-controller-ut-";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long visibleUnreadId;
    private Long otherUserId;
    private Long otherTenantId;
    private Long expiredId;
    private Long deletedId;

    @BeforeEach
    void setUp() {
        cleanNotifications();
        visibleUnreadId = insertNotification(TENANT_A, USER_A, "可见未读通知", null, null, 0, DEDUP_PREFIX + "visible");
        insertNotification(TENANT_A, USER_A, "可见已读通知", "NOW()", null, 0, DEDUP_PREFIX + "read");
        otherUserId = insertNotification(TENANT_A, USER_B, "其他用户通知", null, null, 0, DEDUP_PREFIX + "other-user");
        otherTenantId = insertNotification(PLATFORM, USER_A, "其他租户通知", null, null, 0, DEDUP_PREFIX + "other-tenant");
        expiredId = insertNotification(TENANT_A, USER_A, "过期通知", null, "DATE_SUB(NOW(), INTERVAL 1 DAY)", 0, DEDUP_PREFIX + "expired");
        deletedId = insertNotification(TENANT_A, USER_A, "已删除通知", null, null, 1, DEDUP_PREFIX + "deleted");
    }

    @AfterEach
    void tearDown() {
        cleanNotifications();
    }

    @Test
    void listShouldOnlyReturnVisibleNotificationsForCurrentUserAndTenant() throws Exception {
        mockMvc.perform(get("/api/notifications")
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[?(@.id==" + visibleUnreadId + ")]").exists())
                .andExpect(jsonPath("$.data.records[?(@.id==" + otherUserId + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id==" + otherTenantId + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id==" + expiredId + ")]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.id==" + deletedId + ")]").doesNotExist());
    }

    @Test
    void unreadCountShouldIgnoreReadExpiredDeletedAndCrossScopeNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications/unread-count")
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void markReadShouldOnlyUpdateCurrentVisibleNotification() throws Exception {
        mockMvc.perform(put("/api/notifications/{notificationId}/read", visibleUnreadId)
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(visibleUnreadId))
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").isNumber());

        Integer unread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_notification WHERE id = ? AND read_at IS NULL",
                Integer.class,
                visibleUnreadId
        );
        org.junit.jupiter.api.Assertions.assertEquals(0, unread);
    }

    @Test
    void markReadShouldRejectCrossUserNotification() throws Exception {
        mockMvc.perform(put("/api/notifications/{notificationId}/read", otherUserId)
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void markReadShouldRejectExpiredNotification() throws Exception {
        mockMvc.perform(put("/api/notifications/{notificationId}/read", expiredId)
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void markAllReadShouldBatchUpdateOnlyCurrentVisibleUnreadNotifications() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all")
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        Integer currentUnread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_notification WHERE tenant_id = ? AND recipient_user_id = ? AND deleted = 0 AND read_at IS NULL AND (expires_at IS NULL OR expires_at > NOW()) AND dedup_key LIKE ?",
                Integer.class,
                TENANT_A,
                USER_A,
                DEDUP_PREFIX + "%"
        );
        Integer otherUserUnread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_notification WHERE id = ? AND read_at IS NULL",
                Integer.class,
                otherUserId
        );
        Integer expiredUnread = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_notification WHERE id = ? AND read_at IS NULL",
                Integer.class,
                expiredId
        );
        org.junit.jupiter.api.Assertions.assertEquals(0, currentUnread);
        org.junit.jupiter.api.Assertions.assertEquals(1, otherUserUnread);
        org.junit.jupiter.api.Assertions.assertEquals(1, expiredUnread);
    }

    @Test
    void legacyAccountNotificationPathShouldRemainCompatible() throws Exception {
        mockMvc.perform(get("/api/account/notifications/unread-count")
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void streamShouldReturnSseErrorWhenTicketIsMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event:error")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("UNAUTHORIZED")));
    }

    private Long insertNotification(
            String tenantId,
            Long recipientUserId,
            String title,
            String readAtSql,
            String expiresAtSql,
            int deleted,
            String dedupKey
    ) {
        String readAtExpression = readAtSql == null ? "NULL" : readAtSql;
        String expiresAtExpression = expiresAtSql == null ? "NULL" : expiresAtSql;
        jdbcTemplate.update(
                """
                        INSERT INTO sys_user_notification(
                            tenant_id, recipient_user_id, scenario_code, source_type, source_id,
                            biz_type, biz_id, title, content, level, link,
                            action_payload_json, metadata_json, dedup_key, read_at, expires_at,
                            created_by, updated_by, deleted, created_at, updated_at
                        ) VALUES(?,?,?,?,?,?,?,?,?,?,?,NULL,NULL,?,""" + readAtExpression + "," + expiresAtExpression + ",?,?,?,NOW(),NOW())",
                tenantId,
                recipientUserId,
                "UNIT_TEST",
                "UNIT_TEST",
                dedupKey,
                "UNIT_TEST",
                dedupKey,
                title,
                "通知测试内容",
                "INFO",
                "/unit-test",
                dedupKey,
                "notification-ut",
                "notification-ut",
                deleted
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user_notification WHERE tenant_id = ? AND recipient_user_id = ? AND dedup_key = ?",
                Long.class,
                tenantId,
                recipientUserId,
                dedupKey
        );
    }

    private void cleanNotifications() {
        jdbcTemplate.update("DELETE FROM sys_user_notification WHERE dedup_key LIKE ?", DEDUP_PREFIX + "%");
    }

    private UserAccount principal(String tenantId, Long userId) {
        return new UserAccount(
                userId,
                tenantId,
                "notification_controller_ut_" + userId,
                "mock-password",
                true,
                Set.of(),
                Set.of(),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
