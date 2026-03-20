package com.enterprise.auth.platform.audit;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    private static final String EVENT_TYPE = "AUDIT_EXPORT_UT";
    private static final String REQUEST_ID_VISIBLE = "audit-export-visible";
    private static final String REQUEST_ID_HIDDEN = "audit-export-hidden";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
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
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE event_type = ?", EVENT_TYPE);
    }

    @Test
    void listShouldSupportClientIpFilter() throws Exception {
        mockMvc.perform(get("/api/audit/events")
                        .with(user(principal()))
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
        mockMvc.perform(get("/api/audit/events/export")
                        .with(user(principal()))
                        .header("X-Tenant-Id", "platform")
                        .param("eventType", EVENT_TYPE)
                        .param("clientIp", "10.10.10.10")
                        .param("occurredFrom", java.time.Instant.now().minusSeconds(3600).toString())
                        .param("occurredTo", java.time.Instant.now().plusSeconds(3600).toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(REQUEST_ID_VISIBLE)))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(REQUEST_ID_HIDDEN))));
    }

    private UserAccount principal() {
        return new UserAccount(
                1L,
                "platform",
                "admin",
                passwordEncoder.encode("AuditController@123"),
                true,
                Set.of(),
                Set.of("audit:read"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
