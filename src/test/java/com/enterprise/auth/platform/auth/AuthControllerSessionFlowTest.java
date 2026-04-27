package com.enterprise.auth.platform.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.auth.service.CaptchaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.security.PasswordHasher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.security.redis.session-enabled=false",
        "app.security.redis.captcha-enabled=false"
})
@AutoConfigureMockMvc
class AuthControllerSessionFlowTest {

    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_TENANT = "platform";
    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_USER = "session_flow_tenant_user";
    private static final String TENANT_USER_PASSWORD = "Tenant@123456";
    private static final String CAPTCHA_ID = "captcha-ut";
    private static final String CAPTCHA_CODE = "24682";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CaptchaService captchaService;

    private String previousPasswordHash;

    @BeforeEach
    void setUp() {
        previousPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                String.class,
                "platform",
                "admin"
        );
        jdbcTemplate.update(
                "UPDATE sys_user SET password_hash = ? WHERE tenant_id = ? AND username = ? AND deleted = 0",
                passwordHasher.hash(ADMIN_PASSWORD),
                "platform",
                "admin"
        );

        when(captchaService.create()).thenReturn(new CaptchaService.CaptchaChallenge(
                CAPTCHA_ID,
                "background-base64",
                "slider-base64",
                320,
                180,
                64,
                180
        ));
        doAnswer(invocation -> null).when(captchaService).validate(anyString(), anyString());
    }

    @AfterEach
    void tearDown() {
        if (previousPasswordHash != null) {
            jdbcTemplate.update(
                    "UPDATE sys_user SET password_hash = ? WHERE tenant_id = ? AND username = ? AND deleted = 0",
                    previousPasswordHash,
                    "platform",
                    "admin"
            );
        }
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id IN (SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?)", "tenant-a", "tenant-a", TENANT_USER);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", TENANT_USER);
    }

    @Test
    void loginShouldReturnCaptchaAndCompleteBearerSessionFlow() throws Exception {
        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.captchaId").value(CAPTCHA_ID))
                .andExpect(jsonPath("$.data.backgroundImage").value("background-base64"))
                .andExpect(jsonPath("$.data.sliderImage").value("slider-base64"))
                .andExpect(jsonPath("$.data.backgroundImageWidth").value(320))
                .andExpect(jsonPath("$.data.backgroundImageHeight").value(180))
                .andExpect(jsonPath("$.data.sliderImageWidth").value(64))
                .andExpect(jsonPath("$.data.sliderImageHeight").value(180));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "%s",
                                  "captchaId": "%s",
                                  "captchaCode": "%s",
                                  "device": "test-browser"
                                }
                                """.formatted(ADMIN_PASSWORD, CAPTCHA_ID, CAPTCHA_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginBody.path("data").path("token").asText();
        Assertions.assertFalse(token.isBlank(), "missing bearer token");
        String authorization = "Bearer " + token;

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", authorization)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.grants[?(@=='tenant:read')]").exists());

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", authorization)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.sessionId=='" + token + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.sessionId=='" + token + "' && @.lastAccessAt > 0)]").exists())
                .andExpect(jsonPath("$.data[0].tenantId").value("platform"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", authorization)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
        JsonNode logoutPayload = auditPayload("LOGOUT", token);
        Assertions.assertEquals(token, logoutPayload.path("sessionId").asText());
        Assertions.assertEquals("admin", logoutPayload.path("targetUsername").asText());
        Assertions.assertEquals("platform", logoutPayload.path("targetTenantId").asText());
        Assertions.assertEquals("test-browser", logoutPayload.path("targetDevice").asText());
        Assertions.assertTrue(logoutPayload.path("issuedAt").asLong() > 0, "logout audit should include issuedAt");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", authorization)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void loginShouldIgnoreExistingBearerAndResolveTenantByUsername() throws Exception {
        ensureTenantUser();

        MvcResult adminLoginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "%s",
                                  "captchaId": "%s",
                                  "captchaCode": "%s",
                                  "device": "test-browser"
                                }
                                """.formatted(ADMIN_PASSWORD, CAPTCHA_ID, CAPTCHA_CODE)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(adminLoginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
        Assertions.assertFalse(adminToken.isBlank(), "missing admin bearer token");

        mockMvc.perform(post("/api/auth/login")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s",
                                  "captchaId": "%s",
                                  "captchaCode": "%s",
                                  "device": "test-browser"
                                }
                                """.formatted(TENANT_USER, TENANT_USER_PASSWORD, CAPTCHA_ID, CAPTCHA_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tenantId").value("tenant-a"));
    }

    @Test
    void kickedTokenShouldReturnSessionOffline() throws Exception {
        MvcResult loginResult = loginAsAdmin();
        String token = extractToken(loginResult);

        mockMvc.perform(post("/api/auth/sessions/{sessionId}/offline", token)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_OFFLINE"));
    }

    @Test
    void forceOfflineOwnOtherSessionShouldSucceed() throws Exception {
        MvcResult r1 = loginAsAdmin();
        MvcResult r2 = loginAsAdmin();
        String t1 = extractToken(r1);
        String t2 = extractToken(r2);
        Assertions.assertNotEquals(t1, t2, "should produce distinct tokens");

        mockMvc.perform(post("/api/auth/sessions/{sessionId}/offline", t2)
                        .header("Authorization", "Bearer " + t1)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk());
        JsonNode offlinePayload = auditPayload("SESSION_FORCED_OFFLINE", t2);
        Assertions.assertEquals(t2, offlinePayload.path("sessionId").asText());
        Assertions.assertEquals("admin", offlinePayload.path("targetUsername").asText());
        Assertions.assertEquals(ADMIN_TENANT, offlinePayload.path("targetTenantId").asText());
        Assertions.assertEquals("test-browser", offlinePayload.path("targetDevice").asText());
        Assertions.assertTrue(offlinePayload.path("targetUserId").asLong() > 0, "offline audit should include targetUserId");
        Assertions.assertTrue(offlinePayload.path("issuedAt").asLong() > 0, "offline audit should include issuedAt");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + t2)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_OFFLINE"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + t1)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void sessionsShouldReturnCurrentSessionMark() throws Exception {
        MvcResult loginResult = loginAsAdmin();
        String token = extractToken(loginResult);

        MvcResult response = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").isNumber())
                .andExpect(jsonPath("$.data[?(@.sessionId=='" + token + "')].currentSession").value(true))
                .andReturn();
        JsonNode current = sessionByToken(response, token);
        Assertions.assertTrue(current.path("active").asBoolean(), "current session should be active");
        Assertions.assertEquals(ADMIN_TENANT, current.path("tenantId").asText());
    }

    @Test
    void sessionsAllScopeShouldReturnTenantSessions() throws Exception {
        ensureTenantUser();
        MvcResult adminLogin = loginAsAdmin();
        MvcResult tenantLogin = loginAs(TENANT_USER, TENANT_USER_PASSWORD, TENANT_A);
        String adminToken = extractToken(adminLogin);
        String tenantToken = extractToken(tenantLogin);

        MvcResult response = mockMvc.perform(get("/api/auth/sessions?scope=all")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sessions = dataArray(response);
        Assertions.assertTrue(sessions.size() >= 2, "all scope should include visible active sessions");
        Assertions.assertEquals(ADMIN_TENANT, sessionByToken(response, adminToken).path("tenantId").asText());
        Assertions.assertEquals(TENANT_A, sessionByToken(response, tenantToken).path("tenantId").asText());
        for (JsonNode session : sessions) {
            Assertions.assertTrue(session.path("active").asBoolean(), "all scope should only return active sessions");
        }
    }

    @Test
    void forceOfflineNonexistentSessionShouldFail() throws Exception {
        MvcResult loginResult = loginAsAdmin();
        String token = extractToken(loginResult);

        mockMvc.perform(post("/api/auth/sessions/{sessionId}/offline", "nonexistent-session-uuid")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void sessionsSortedByLastAccessDesc() throws Exception {
        MvcResult r1 = loginAsAdmin();
        MvcResult r2 = loginAsAdmin();
        String t1 = extractToken(r1);
        String t2 = extractToken(r2);
        Assertions.assertNotEquals(t1, t2, "should produce distinct tokens");

        // Access /me with t2 to update its lastAccessAt, so t2 > t1
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + t2)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk());

        // Verify sessions are returned sorted by lastAccessAt descending
        MvcResult response = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + t1)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode sessions = dataArray(response);
        Assertions.assertTrue(sessions.size() >= 2, "own scope should include both admin sessions");
        assertSortedByLastAccessDesc(sessions);
        Assertions.assertNotNull(sessionByToken(response, t1));
        Assertions.assertNotNull(sessionByToken(response, t2));
    }

    private MvcResult loginAsAdmin() throws Exception {
        return loginAs(ADMIN_USERNAME, ADMIN_PASSWORD, ADMIN_TENANT);
    }

    private MvcResult loginAs(String username, String password, String tenantId) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginPayload(username, password, tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
    }

    private String extractToken(MvcResult loginResult) {
        try {
            JsonNode body = objectMapper.readTree(loginResult.getResponse().getContentAsString());
            String token = body.path("data").path("token").asText();
            if (token.isBlank()) {
                throw new RuntimeException("missing bearer token");
            }
            return token;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode dataArray(MvcResult result) throws Exception {
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Assertions.assertTrue(data.isArray(), "response data should be an array");
        return data;
    }

    private JsonNode sessionByToken(MvcResult result, String token) throws Exception {
        for (JsonNode session : dataArray(result)) {
            if (token.equals(session.path("sessionId").asText())) {
                return session;
            }
        }
        throw new AssertionError("session not found: " + token);
    }

    private JsonNode auditPayload(String eventType, String sessionId) throws Exception {
        List<String> payloads = jdbcTemplate.queryForList(
                """
                SELECT payload_json
                FROM sys_audit_log
                WHERE event_type = ? AND payload_json LIKE ?
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class,
                eventType,
                "%" + sessionId + "%"
        );
        Assertions.assertFalse(payloads.isEmpty(), "missing audit event " + eventType + " for session " + sessionId);
        return objectMapper.readTree(payloads.get(0));
    }

    private void assertSortedByLastAccessDesc(JsonNode sessions) {
        long previous = Long.MAX_VALUE;
        for (JsonNode session : sessions) {
            long current = session.path("lastAccessAt").asLong();
            Assertions.assertTrue(previous >= current, "sessions should be sorted by lastAccessAt desc");
            previous = current;
        }
    }

    private String loginPayload(String username, String password, String tenantId) {
        String tenantLine = tenantId == null ? "" : """
                  "tenantId": "%s",
                """.formatted(tenantId);
        return """
                {
                  "username": "%s",
                  "password": "%s",
                %s
                  "captchaId": "%s",
                  "captchaCode": "%s",
                  "device": "test-browser"
                }
                """.formatted(username, password, tenantLine, CAPTCHA_ID, CAPTCHA_CODE);
    }

    private void ensureTenantUser() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id IN (SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?)", "tenant-a", "tenant-a", TENANT_USER);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", TENANT_USER);
        jdbcTemplate.update(
                """
                INSERT INTO sys_user (
                    tenant_id, dept_id, username, display_name, password_hash,
                    enabled, session_version, created_by, updated_by, deleted, password_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW())
                """,
                "tenant-a",
                2L,
                TENANT_USER,
                TENANT_USER,
                passwordHasher.hash(TENANT_USER_PASSWORD),
                1,
                1,
                "test",
                "test"
        );
        jdbcTemplate.update(
                "INSERT INTO sys_user_role (tenant_id, user_id, role_id, created_by, updated_by) VALUES (?, (SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0), 2, 'test', 'test')",
                "tenant-a",
                "tenant-a",
                TENANT_USER
        );
    }

}
