package com.enterprise.auth.platform.tenant;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantControllerTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String STATUS_SUMMARY = "历史筛选-状态";
    private static final String PACKAGE_SUMMARY = "历史筛选-套餐";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 3600 * 1000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)",
                TENANT_ID, STATUS_SUMMARY, PACKAGE_SUMMARY
        );
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_capability_override WHERE tenant_id = ? AND capability_code IN (?, ?)",
                TENANT_ID, "audit", "notice"
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,DATE_SUB(NOW(), INTERVAL 2 DAY))",
                TENANT_ID, "STATUS", "tenantStatus", "0", "1", STATUS_SUMMARY, "tester"
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,NOW())",
                TENANT_ID, "PACKAGE", "packageCode", "basic", "pro", PACKAGE_SUMMARY, "another"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)",
                TENANT_ID, STATUS_SUMMARY, PACKAGE_SUMMARY
        );
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_capability_override WHERE tenant_id = ? AND capability_code IN (?, ?)",
                TENANT_ID, "audit", "notice"
        );
    }

    @Test
    void currentTenantResolvesFromHeader() throws Exception {
        mockMvc.perform(get("/api/tenants/current")
                        .with(bearer(principal("tenant:read"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID));
    }

    @Test
    void tenantHistoryShouldSupportFilters() throws Exception {
        long now = System.currentTimeMillis();
        mockMvc.perform(get("/api/tenants/{tenantId}/history", TENANT_ID)
                        .with(bearer(principal("tenant:read"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("changeType", "STATUS")
                        .param("operator", "test")
                        .param("fromEpochMs", String.valueOf(now - SEVEN_DAYS_MS))
                        .param("toEpochMs", String.valueOf(now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].changeType").value("STATUS"))
                .andExpect(jsonPath("$.data.records[0].summary").value(STATUS_SUMMARY))
                .andExpect(jsonPath("$.data.records[0].impactSummary").isNotEmpty());
    }

    @Test
    void tenantHistorySummaryShouldReturnTrajectoryOverview() throws Exception {
        long now = System.currentTimeMillis();
        mockMvc.perform(get("/api/tenants/{tenantId}/history/summary", TENANT_ID)
                        .with(bearer(principal("tenant:read"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("fromEpochMs", String.valueOf(now - SEVEN_DAYS_MS))
                        .param("toEpochMs", String.valueOf(now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.totalChanges").isNumber())
                .andExpect(jsonPath("$.data.packageChanges").isNumber())
                .andExpect(jsonPath("$.data.recentTimeline").isArray());
    }

    @Test
    void tenantHistoryShouldRejectLocalDateTimeWithoutTimezone() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}/history", TENANT_ID)
                        .with(bearer(principal("tenant:read"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("fromEpochMs", "2026-03-01T00:00:00")
                        .param("toEpochMs", "2026-03-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantCapabilityOverridesShouldBeQueriedAndUpdated() throws Exception {
        mockMvc.perform(put("/api/tenants/{tenantId}/capability-overrides", TENANT_ID)
                        .with(bearer(principal("tenant:write"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "capabilityCode": "audit",
                                      "enabled": true,
                                      "capabilityDescOverride": "审计导出与看板能力"
                                    },
                                    {
                                      "capabilityCode": "notice",
                                      "enabled": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.overrides[?(@.capabilityCode=='audit')].overrideEnabled").value(true));

        mockMvc.perform(get("/api/tenants/{tenantId}/capability-overrides", TENANT_ID)
                        .with(bearer(principal("tenant:read"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.overrides[?(@.capabilityCode=='audit')].effectiveEnabled").value(true))
                .andExpect(jsonPath("$.data.overrides[?(@.capabilityCode=='notice')].effectiveEnabled").value(false));
    }

    private UserAccount principal(String authority) {
        java.util.LinkedHashSet<String> authorities = new java.util.LinkedHashSet<>();
        authorities.add("tenant:read");
        authorities.add("tenant:write");
        authorities.add(authority);
        return new UserAccount(
                1L,
                "platform",
                "tester",
                "{noop}ignored",
                true,
                Set.of("TENANT_ADMIN"),
                authorities,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
