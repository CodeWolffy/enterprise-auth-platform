package com.enterprise.auth.platform.tenant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantControllerTest {

    private static final String TENANT_ID = "tenant-a";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)", TENANT_ID, "历史筛选-状态", "历史筛选-套餐");
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,DATE_SUB(NOW(), INTERVAL 2 DAY))",
                TENANT_ID, "STATUS", "tenantStatus", "0", "1", "历史筛选-状态", "tester"
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,NOW())",
                TENANT_ID, "PACKAGE", "packageCode", "basic", "pro", "历史筛选-套餐", "another"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)", TENANT_ID, "历史筛选-状态", "历史筛选-套餐");
    }

    @Test
    void currentTenantResolvesFromHeader() throws Exception {
        UserAccount user = new UserAccount(
                1L,
                "tenant-a",
                "tester",
                "{noop}ignored",
                true,
                Set.of("TENANT_ADMIN"),
                Set.of("tenant:read"),
                Set.of(),
                DataScopeType.ALL,
                1
        );

        mockMvc.perform(get("/api/tenants/current")
                        .with(user(user))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value("tenant-a"));
    }

    @Test
    void tenantHistoryShouldSupportFilters() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}/history", TENANT_ID)
                        .with(user(principal()))
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("changeType", "STATUS")
                        .param("operator", "test")
                        .param("occurredFrom", java.time.Instant.now().minusSeconds(7 * 24 * 3600).toString())
                        .param("occurredTo", java.time.Instant.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].changeType").value("STATUS"))
                .andExpect(jsonPath("$.data.records[0].summary").value("历史筛选-状态"))
                .andExpect(jsonPath("$.data.records[0].impactSummary").isNotEmpty())
                .andExpect(jsonPath("$.data.records[?(@.summary=='历史筛选-套餐')]").doesNotExist());
    }

    private UserAccount principal() {
        return new UserAccount(
                1L,
                TENANT_ID,
                "tester",
                "{noop}ignored",
                true,
                Set.of("TENANT_ADMIN"),
                Set.of("tenant:read"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
