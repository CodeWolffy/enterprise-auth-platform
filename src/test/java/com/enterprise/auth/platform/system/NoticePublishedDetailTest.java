package com.enterprise.auth.platform.system;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.test.web.servlet.MockMvc;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class NoticePublishedDetailTest {

    private static final String TENANT_A = "tenant-a";
    private static final String OTHER_TENANT = "tenant-b";
    private static final Long USER_A = 2L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long publishedNoticeId;
    private Long platformPublishedNoticeId;
    private Long draftNoticeId;
    private Long scheduledNoticeId;
    private Long otherTenantNoticeId;

    @BeforeEach
    void setUp() {
        cleanNotices();
        publishedNoticeId = insertNotice(TENANT_A, "已发布公告", "<p>内容</p>", 1, null);
        platformPublishedNoticeId = insertNotice("platform", "平台已发布公告", "<p>平台内容</p>", 1, null);
        draftNoticeId = insertNotice(TENANT_A, "草稿公告", "<p>草稿</p>", 0, null);
        scheduledNoticeId = insertNotice(TENANT_A, "待发布公告", "<p>待发布</p>", 1, "2099-01-01 00:00:00");
        otherTenantNoticeId = insertNotice(OTHER_TENANT, "其他租户公告", "<p>其他</p>", 1, null);
    }

    @AfterEach
    void tearDown() {
        cleanNotices();
    }

    @Test
    void publishedNoticeShouldBeAccessibleWithoutSysnoticePagePermission() throws Exception {
        mockMvc.perform(get("/api/system/notices/{id}/published", publishedNoticeId)
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(publishedNoticeId))
                .andExpect(jsonPath("$.data.noticeTitle").value("已发布公告"))
                .andExpect(jsonPath("$.data.published").value(true));
    }

    @Test
    void platformTenantPublishedNoticeShouldAlsoBeAccessible() throws Exception {
        mockMvc.perform(get("/api/system/notices/{id}/published", platformPublishedNoticeId)
                        .with(bearer(principal("platform", 1L), "platform"))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(platformPublishedNoticeId))
                .andExpect(jsonPath("$.data.noticeTitle").value("平台已发布公告"));
    }

    @Test
    void draftNoticeShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/system/notices/{id}/published", draftNoticeId)
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("公告不存在或尚未发布"));
    }

    @Test
    void scheduledNoticeShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/system/notices/{id}/published", scheduledNoticeId)
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("公告不存在或尚未发布"));
    }

    @Test
    void crossTenantNoticeShouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/system/notices/{id}/published", otherTenantNoticeId)
                        .with(bearer(principal(TENANT_A, USER_A), TENANT_A))
                        .header("X-Tenant-Id", TENANT_A))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("公告不存在或尚未发布"));
    }

    private Long insertNotice(String tenantId, String title, String content, int published, String publishTime) {
        jdbcTemplate.update(
                "INSERT INTO sys_notice (tenant_id, notice_title, notice_content, published, publish_time, created_by, updated_by, deleted) VALUES (?, ?, ?, ?, ?, 'system', 'system', 0)",
                tenantId, title, content, published, publishTime);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_notice WHERE tenant_id = ? AND notice_title = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                tenantId, title);
    }

    private void cleanNotices() {
        jdbcTemplate.update("DELETE FROM sys_notice WHERE notice_title IN (?, ?, ?, ?, ?)", "已发布公告", "平台已发布公告", "草稿公告", "待发布公告", "其他租户公告");
    }

    private UserAccount principal(String tenantId, Long userId) {
        Integer sessionVersion = jdbcTemplate.queryForObject(
                "SELECT session_version FROM sys_user WHERE id = ? AND tenant_id = ? AND deleted = 0",
                Integer.class,
                userId,
                tenantId
        );
        return new UserAccount(
                userId,
                tenantId,
                "notice_published_ut_" + userId,
                "mock-password",
                true,
                Set.of(),
                Set.of(),
                Set.of(),
                DataScopeType.ALL,
                sessionVersion == null ? 1 : sessionVersion
        );
    }
}
