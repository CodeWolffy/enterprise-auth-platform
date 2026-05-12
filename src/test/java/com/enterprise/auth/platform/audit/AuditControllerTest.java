package com.enterprise.auth.platform.audit;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.dto.model.DataScopeType;
import com.enterprise.auth.platform.dto.model.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.security.PasswordHasher;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    private static final String EVENT_TYPE = "AUDIT_EXPORT_UT";
    private static final String REQUEST_ID_VISIBLE = "audit-export-visible";
    private static final String REQUEST_ID_HIDDEN = "audit-export-hidden";
    private static final long ONE_HOUR_MS = 3600_000L;
    private static final long ONE_DAY_MS = 24 * ONE_HOUR_MS;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_audit_export_task WHERE query_json LIKE ?", "%AUDIT_EXPORT_UT%");
        jdbcTemplate.update("DELETE FROM sys_audit_export_task WHERE file_name LIKE 'audit-governance-ut-%'");
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE event_type = ?", EVENT_TYPE);
        jdbcTemplate.update(
                "INSERT INTO sys_audit_log(tenant_id, event_type, operator, payload_json, occurred_at, request_id, client_ip) VALUES(?,?,?,?,NOW(),?,?)",
                "platform", EVENT_TYPE, "admin", "{\"bizId\":\"visible\"}", REQUEST_ID_VISIBLE, "10.10.10.10"
        );
        jdbcTemplate.update(
                "INSERT INTO sys_audit_log(tenant_id, event_type, operator, payload_json, occurred_at, request_id, client_ip) VALUES(?,?,?,?,NOW(),?,?)",
                "platform", EVENT_TYPE, "admin", "{\"bizId\":\"hidden\"}", REQUEST_ID_HIDDEN, "10.10.10.20"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_audit_export_task WHERE query_json LIKE ?", "%AUDIT_EXPORT_UT%");
        jdbcTemplate.update("DELETE FROM sys_audit_export_task WHERE file_name LIKE 'audit-governance-ut-%'");
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE event_type = ?", EVENT_TYPE);
    }

    @Test
    void listShouldSupportClientIpFilter() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                        .with(bearer(principal()))
                        .header("X-Tenant-Id", "platform")
                        .param("eventType", EVENT_TYPE)
                        .param("clientIp", "10.10.10.10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].clientIp").value("10.10.10.10"))
                .andExpect(jsonPath("$.data.records[0].requestId").value(REQUEST_ID_VISIBLE))
                .andExpect(jsonPath("$.data.records[?(@.requestId=='" + REQUEST_ID_HIDDEN + "')]").doesNotExist());
    }

    @Test
    void exportShouldSupportClientIpFilter() throws Exception {
                long now = System.currentTimeMillis();
                mockMvc.perform(get("/api/audit/events/export")
                                .with(bearer(principalWithWrite()))
                                .header("X-Tenant-Id", "platform")
                                .param("eventType", EVENT_TYPE)
                                .param("clientIp", "10.10.10.10")
                                .param("fromEpochMs", String.valueOf(now - ONE_HOUR_MS))
                                .param("toEpochMs", String.valueOf(now + ONE_HOUR_MS)))
                        .andExpect(status().isOk())
                        .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                                result.getResponse().getContentAsByteArray().length > 0
                        ));
    }

    @Test
    void eventsShouldRejectLocalDateTimeWithoutTimezone() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                        .with(bearer(principal()))
                        .header("X-Tenant-Id", "platform")
                        .param("eventType", EVENT_TYPE)
                        .param("fromEpochMs", "2026-03-01T00:00:00")
                        .param("toEpochMs", "2026-03-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldCreateAsyncExportTask() throws Exception {
        long now = System.currentTimeMillis();
        mockMvc.perform(post("/api/audit/exports")
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform")
                        .param("eventType", EVENT_TYPE)
                        .param("clientIp", "10.10.10.10")
                        .param("fromEpochMs", String.valueOf(now - ONE_HOUR_MS))
                        .param("toEpochMs", String.valueOf(now + ONE_HOUR_MS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.data.status").isNotEmpty())
                .andExpect(jsonPath("$.data.progressPercent").isNumber())
                .andExpect(jsonPath("$.data.progressStage").isNotEmpty());

        mockMvc.perform(get("/api/audit/exports")
                        .with(bearer(principal()))
                        .header("X-Tenant-Id", "platform")
                        .param("tenantId", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").exists());
    }

    @Test
    void shouldDeleteAndCleanupExportTasks() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, requested_at, completed_at) VALUES(?,?,?,?,?,?,NOW(),DATE_SUB(NOW(), INTERVAL 2 DAY))",
                "platform", "admin", "SUCCESS", "audit-cleanup-ut.csv", "{\"eventType\":\"AUDIT_EXPORT_UT\"}", 1
        );
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_audit_export_task WHERE file_name = ?",
                Long.class,
                "audit-cleanup-ut.csv"
        );

        mockMvc.perform(delete("/api/audit/exports/{taskId}", taskId)
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());

        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, requested_at, completed_at) VALUES(?,?,?,?,?,?,NOW(),DATE_SUB(NOW(), INTERVAL 5 DAY))",
                "platform", "admin", "FAILED", "audit-cleanup-batch-ut.csv", "{\"eventType\":\"AUDIT_EXPORT_UT\"}", 0
        );

        mockMvc.perform(delete("/api/audit/exports")
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform")
                        .param("tenantId", "platform")
                        .param("status", "FAILED")
                        .param("completedBeforeEpochMs", String.valueOf(System.currentTimeMillis() - ONE_DAY_MS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void shouldArchiveSingleAndBatchExportTasks() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, file_content, requested_at, completed_at) VALUES(?,?,?,?,?,?,?,NOW(),DATE_SUB(NOW(), INTERVAL 2 DAY))",
                "platform", "admin", "SUCCESS", "audit-archive-single-ut.csv", "{\"eventType\":\"AUDIT_EXPORT_UT\"}", 1, "csv".getBytes()
        );
        Long singleTaskId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_audit_export_task WHERE file_name = ?",
                Long.class,
                "audit-archive-single-ut.csv"
        );

        mockMvc.perform(post("/api/audit/exports/{taskId}/archive", singleTaskId)
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.archived").value(true))
                .andExpect(jsonPath("$.data.archivable").value(false));

        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, file_content, requested_at, completed_at) VALUES(?,?,?,?,?,?,?,NOW(),DATE_SUB(NOW(), INTERVAL 5 DAY))",
                "platform", "admin", "SUCCESS", "audit-archive-batch-ut.csv", "{\"eventType\":\"AUDIT_EXPORT_UT\"}", 1, "csv".getBytes()
        );

        mockMvc.perform(post("/api/audit/exports/archive")
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform")
                        .param("tenantId", "platform")
                        .param("status", "SUCCESS")
                        .param("completedBeforeEpochMs", String.valueOf(System.currentTimeMillis() - ONE_DAY_MS)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void shouldRetryExportTask() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, requested_at, completed_at, error_message) VALUES(?,?,?,?,?,?,NOW(),NOW(),?)",
                "platform", "admin", "FAILED", "audit-retry-ut.csv",
                "{\"tenantId\":\"platform\",\"eventType\":\"AUDIT_EXPORT_UT\",\"clientIp\":\"10.10.10.10\",\"fromEpochMs\":1742428800000,\"toEpochMs\":1742515200000}",
                0, "mock failed"
        );
        Long taskId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_audit_export_task WHERE file_name = ?",
                Long.class,
                "audit-retry-ut.csv"
        );

        mockMvc.perform(post("/api/audit/exports/{taskId}/retry", taskId)
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").isNotEmpty());
    }

    @Test
    void shouldQueryAndUpdateExportPolicy() throws Exception {
        mockMvc.perform(get("/api/audit/exports/policy")
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionDays").isNumber())
                .andExpect(jsonPath("$.data.maxTasks").isNumber());

        mockMvc.perform(put("/api/audit/exports/policy")
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform")
                        .contentType("application/json")
                        .content("""
                                {
                                  "retentionDays": 9,
                                  "maxTasks": 120
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.retentionDays").value(9))
                .andExpect(jsonPath("$.data.maxTasks").value(120));
    }

    @Test
    void shouldPreviewGovernanceInDryRunMode() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, file_content, requested_at, completed_at) VALUES(?,?,?,?,?,?,?,NOW(),DATE_SUB(NOW(), INTERVAL 10 DAY))",
                "platform", "admin", "SUCCESS", "audit-governance-ut-old.csv", "{\"eventType\":\"AUDIT_EXPORT_UT\"}", 1, "csv".getBytes()
        );
        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, file_content, requested_at, completed_at) VALUES(?,?,?,?,?,?,?,NOW(),DATE_SUB(NOW(), INTERVAL 1 DAY))",
                "platform", "admin", "SUCCESS", "audit-governance-ut-new.csv", "{\"eventType\":\"AUDIT_EXPORT_UT\"}", 1, "csv".getBytes()
        );

        mockMvc.perform(post("/api/audit/exports/governance")
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform")
                        .param("tenantId", "platform")
                        .param("dryRun", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.dryRun").value(true))
                .andExpect(jsonPath("$.data.plannedDeleteCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_audit_export_task WHERE tenant_id = ? AND file_name LIKE 'audit-governance-ut-%'",
                Integer.class,
                "platform"
        );
        org.junit.jupiter.api.Assertions.assertEquals(2, remaining);
    }

    @Test
    void shouldExecuteGovernanceAndDeleteExpiredTasks() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO sys_audit_export_task(tenant_id, operator, status, file_name, query_json, record_count, file_content, requested_at, completed_at) VALUES(?,?,?,?,?,?,?,NOW(),DATE_SUB(NOW(), INTERVAL 10 DAY))",
                "platform", "admin", "SUCCESS", "audit-governance-ut-expired.csv", "{\"eventType\":\"AUDIT_EXPORT_UT\"}", 1, "csv".getBytes()
        );

        mockMvc.perform(post("/api/audit/exports/governance")
                        .with(bearer(principalWithWrite()))
                        .header("X-Tenant-Id", "platform")
                        .param("tenantId", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.dryRun").value(false))
                .andExpect(jsonPath("$.data.deletedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        Integer expiredRemaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_audit_export_task WHERE tenant_id = ? AND file_name = ?",
                Integer.class,
                "platform",
                "audit-governance-ut-expired.csv"
        );
        org.junit.jupiter.api.Assertions.assertEquals(0, expiredRemaining);
    }

    private UserAccount principal() {
        return new UserAccount(
                1L,
                "platform",
                "admin",
                passwordHasher.hash("AuditController@123"),
                true,
                Set.of(),
                Set.of("audit:read"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }

    private UserAccount principalWithWrite() {
        return new UserAccount(
                1L,
                "platform",
                "admin",
                passwordHasher.hash("AuditController@123"),
                true,
                Set.of(),
                Set.of("audit:read", "audit:write"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
