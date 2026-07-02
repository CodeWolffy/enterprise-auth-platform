package com.enterprise.auth.platform.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.modules.auth.application.CaptchaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService;
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
    private AuthPrincipalCacheService authPrincipalCacheService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CaptchaService captchaService;

    private String previousPasswordHash;
    private Integer previousSessionVersion;
    private Integer previousMustChangePassword;
    private java.time.LocalDateTime previousPasswordUpdatedAt;

    @BeforeEach
    void setUp() {
        previousPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                String.class,
                "platform",
                "admin"
        );
        previousSessionVersion = jdbcTemplate.queryForObject(
                "SELECT session_version FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                Integer.class,
                "platform",
                "admin"
        );
        previousMustChangePassword = jdbcTemplate.queryForObject(
                "SELECT must_change_password + 0 FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                Integer.class,
                "platform",
                "admin"
        );
        previousPasswordUpdatedAt = jdbcTemplate.queryForObject(
                "SELECT password_updated_at FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                java.time.LocalDateTime.class,
                "platform",
                "admin"
        );
        jdbcTemplate.update(
                "UPDATE sys_user SET password_hash = ?, must_change_password = 0, password_updated_at = NOW() WHERE tenant_id = ? AND username = ? AND deleted = 0",
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
        doAnswer(invocation -> null).when(captchaService).secondaryVerify(anyString());
        when(captchaService.secondaryVerifyWithoutRemoval(anyString())).thenReturn(true);
        clearAuthState();
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
        if (previousSessionVersion != null) {
            jdbcTemplate.update(
                    "UPDATE sys_user SET session_version = ? WHERE tenant_id = ? AND username = ? AND deleted = 0",
                    previousSessionVersion,
                    "platform",
                    "admin"
            );
        }
        if (previousMustChangePassword != null) {
            jdbcTemplate.update(
                    "UPDATE sys_user SET must_change_password = ?, password_updated_at = ? WHERE tenant_id = ? AND username = ? AND deleted = 0",
                    previousMustChangePassword,
                    previousPasswordUpdatedAt,
                    "platform",
                    "admin"
            );
        }
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id IN (SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?)", "tenant-a", "tenant-a", TENANT_USER);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", TENANT_USER);
        jdbcTemplate.update("DELETE FROM sys_dict WHERE tenant_id = ? AND dict_type = ?", TENANT_A, "tenant_context_audit");
        jdbcTemplate.update("DELETE FROM sys_log WHERE request_id = ?", "tenant-context-audit-ut");
        jdbcTemplate.update("DELETE FROM sys_log WHERE request_id = ?", "tenant-switch-ut");
        clearAuthState();
    }

    private void clearAuthState() {
        authPrincipalCacheService.evictAll();
        if (SaManager.getSaTokenDao() instanceof SaTokenDaoDefaultImpl localDao && localDao.timedCache != null) {
            for (String key : List.copyOf(localDao.timedCache.keySet())) {
                localDao.deleteObject(key);
            }
        }
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
                .andExpect(jsonPath("$.data.grants[?(@=='upms:systenant:page')]").exists());

        SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
        Assertions.assertEquals("platform", tokenSession.get("permissionsTenantId"));
        Assertions.assertNotNull(tokenSession.get("permissions"));
        Assertions.assertNotNull(tokenSession.get("roles"));

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
        JsonNode logoutPayload = logPayload("LOGOUT");
        Assertions.assertEquals("******", logoutPayload.path("sessionId").asText());
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
    void loginShouldRejectTenantOutsideAuthorizationWindow() throws Exception {
        ensureTenantUser();
        java.time.LocalDateTime previousAuthBeginAt = jdbcTemplate.queryForObject(
                "SELECT auth_begin_at FROM sys_tenant WHERE tenant_id = ? AND deleted = 0",
                java.time.LocalDateTime.class,
                TENANT_A
        );
        java.time.LocalDateTime previousExpireAt = jdbcTemplate.queryForObject(
                "SELECT expire_at FROM sys_tenant WHERE tenant_id = ? AND deleted = 0",
                java.time.LocalDateTime.class,
                TENANT_A
        );
        try {
            jdbcTemplate.update("UPDATE sys_tenant SET auth_begin_at = DATE_ADD(NOW(), INTERVAL 1 DAY), expire_at = DATE_ADD(NOW(), INTERVAL 30 DAY) WHERE tenant_id = ? AND deleted = 0", TENANT_A);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload(TENANT_USER, TENANT_USER_PASSWORD, TENANT_A)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TENANT_DISABLED"));

            jdbcTemplate.update("UPDATE sys_tenant SET auth_begin_at = DATE_SUB(NOW(), INTERVAL 30 DAY), expire_at = DATE_SUB(NOW(), INTERVAL 1 DAY) WHERE tenant_id = ? AND deleted = 0", TENANT_A);
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginPayload(TENANT_USER, TENANT_USER_PASSWORD, TENANT_A)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TENANT_DISABLED"));
        } finally {
            jdbcTemplate.update("UPDATE sys_tenant SET auth_begin_at = ?, expire_at = ? WHERE tenant_id = ? AND deleted = 0", previousAuthBeginAt, previousExpireAt, TENANT_A);
        }
    }

    @Test
    void platformAdminMeShouldUseSwitchedTenantSnapshot() throws Exception {
        String token = extractToken(loginAsAdmin());
        mockMvc.perform(post("/api/auth/tenants/{tenantId}/switch", TENANT_A)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.data.operatorTenantId").value(ADMIN_TENANT))
                .andExpect(jsonPath("$.data.superAdmin").value(true))
                .andExpect(jsonPath("$.data.menus[?(@.path=='/dashboard')]").doesNotExist())
                .andExpect(jsonPath("$.data.menus[?(@.path=='/system/logs/operation')]").exists());
    }

    @Test
    void platformAdminSwitchTenantShouldPersistActiveTenantAndAudit() throws Exception {
        String token = extractToken(loginAsAdmin());
        String requestId = "tenant-switch-ut";

        mockMvc.perform(post("/api/auth/tenants/{tenantId}/switch", TENANT_A)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value(ADMIN_USERNAME))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.data.operatorTenantId").value(ADMIN_TENANT))
                .andExpect(jsonPath("$.data.superAdmin").value(true));

        SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
        Assertions.assertEquals(TENANT_A, tokenSession.get("activeTenantId"));
        Assertions.assertEquals(TENANT_A, tokenSession.get("permissionsTenantId"));
        Assertions.assertNotNull(tokenSession.get("permissions"));
        Assertions.assertNotNull(tokenSession.get("roles"));

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.sessionId=='" + token + "' && @.activeTenantId=='" + TENANT_A + "')]").exists());

        JsonNode payload = latestLogPayload("TENANT_SWITCH", requestId);
        Assertions.assertEquals(ADMIN_TENANT, payload.path("operatorTenantId").asText());
        Assertions.assertEquals(ADMIN_TENANT, payload.path("fromTenantId").asText());
        Assertions.assertEquals(TENANT_A, payload.path("activeTenantId").asText());
        Assertions.assertEquals(TENANT_A, payload.path("targetTenantId").asText());
        Assertions.assertEquals("******", payload.path("sessionId").asText());
    }

    @Test
    void platformAdminSwitchTenantShouldRejectMissingTenant() throws Exception {
        String token = extractToken(loginAsAdmin());

        mockMvc.perform(post("/api/auth/tenants/{tenantId}/switch", "missing-tenant-ut")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TENANT_NOT_FOUND"));
    }

    @Test
    void platformAdminSwitchTenantShouldRejectDisabledTenant() throws Exception {
        String token = extractToken(loginAsAdmin());
        Integer previousStatus = jdbcTemplate.queryForObject(
                "SELECT tenant_status FROM sys_tenant WHERE tenant_id = ? AND deleted = 0",
                Integer.class,
                TENANT_A
        );
        try {
            jdbcTemplate.update("UPDATE sys_tenant SET tenant_status = 0 WHERE tenant_id = ? AND deleted = 0", TENANT_A);

            mockMvc.perform(post("/api/auth/tenants/{tenantId}/switch", TENANT_A)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TENANT_DISABLED"));
        } finally {
            jdbcTemplate.update("UPDATE sys_tenant SET tenant_status = ? WHERE tenant_id = ? AND deleted = 0", previousStatus, TENANT_A);
        }
    }

    @Test
    void tenantUserSwitchTenantShouldRejectOtherTenant() throws Exception {
        ensureTenantUser();
        String token = extractToken(loginAs(TENANT_USER, TENANT_USER_PASSWORD, TENANT_A));

        mockMvc.perform(post("/api/auth/tenants/{tenantId}/switch", ADMIN_TENANT)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void tenantUserMeShouldIgnoreForgedTenantHeader() throws Exception {
        ensureTenantUser();
        String token = extractToken(loginAs(TENANT_USER, TENANT_USER_PASSWORD, TENANT_A));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value(TENANT_USER))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_A))
                .andExpect(jsonPath("$.data.operatorTenantId").value(TENANT_A))
                .andExpect(jsonPath("$.data.superAdmin").value(false))
                .andExpect(jsonPath("$.data.grants[?(@=='upms:systenant:page')]").doesNotExist())
                .andExpect(jsonPath("$.data.grants[?(@=='upms:operationlog:page')]").exists());
    }

    @Test
    void passwordChangeRequiredSessionShouldRestrictAccessButAllowLogoutAndPasswordChange() throws Exception {
        jdbcTemplate.update(
                "UPDATE sys_user SET must_change_password = 1 WHERE tenant_id = ? AND username = ? AND deleted = 0",
                ADMIN_TENANT,
                ADMIN_USERNAME
        );

        MvcResult firstLogin = loginAsAdmin();
        JsonNode firstLoginData = objectMapper.readTree(firstLogin.getResponse().getContentAsString()).path("data");
        Assertions.assertTrue(firstLoginData.path("passwordChangeRequired").asBoolean(), "login should enter restricted password change state");
        Assertions.assertEquals("FORCE_CHANGE", firstLoginData.path("passwordChangeReason").asText());
        String firstToken = firstLoginData.path("token").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + firstToken)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isUnauthorized());

        MvcResult secondLogin = loginAsAdmin();
        String secondToken = extractToken(secondLogin);

        mockMvc.perform(post("/api/account/password/change")
                        .header("Authorization", "Bearer " + secondToken)
                        .header("X-Tenant-Id", ADMIN_TENANT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "oldPassword": "%s",
                                  "newPassword": "AdminForced@123456"
                                }
                                """.formatted(ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false));

        SaSession tokenSession = StpUtil.getTokenSessionByToken(secondToken);
        Assertions.assertEquals(false, tokenSession.get("passwordChangeRequired"));

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + secondToken)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void auditPayloadShouldIncludeEffectiveTenantContext() throws Exception {
        String token = extractToken(loginAsAdmin());
        String requestId = "tenant-context-audit-ut";
        jdbcTemplate.update("DELETE FROM sys_dict WHERE tenant_id = ? AND dict_type = ?", TENANT_A, "tenant_context_audit");
        jdbcTemplate.update("DELETE FROM sys_audit_log WHERE request_id = ?", requestId);

        mockMvc.perform(post("/api/auth/tenants/{tenantId}/switch", TENANT_A)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", requestId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/system/dicts")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictType": "tenant_context_audit",
                                  "description": "Tenant Context Audit"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        JsonNode payload = latestAuditPayload("DICT_CREATED", requestId);
        Assertions.assertEquals(TENANT_A, payload.path("activeTenantId").asText());
        Assertions.assertEquals(ADMIN_TENANT, payload.path("operatorTenantId").asText());
        Assertions.assertEquals(ADMIN_USERNAME, payload.path("operator").asText());
        Assertions.assertEquals(requestId, payload.path("requestId").asText());
    }

    @Test
    void kickedTokenShouldReturnSessionOffline() throws Exception {
        MvcResult loginResult = loginAsAdmin();
        String token = extractToken(loginResult);

        StpUtil.kickoutByTokenValue(token);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_OFFLINE"));
    }

    @Test
    void changedSessionVersionShouldKickExistingToken() throws Exception {
        MvcResult loginResult = loginAsAdmin();
        String token = extractToken(loginResult);
        jdbcTemplate.update(
                "UPDATE sys_user SET session_version = session_version + 1 WHERE tenant_id = ? AND username = ? AND deleted = 0",
                ADMIN_TENANT,
                ADMIN_USERNAME
        );
        authPrincipalCacheService.evictByUser(1L, ADMIN_TENANT, ADMIN_USERNAME);

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_OFFLINE"));
    }

    @Test
    void tokenUsedFromDifferentClientIpShouldBeKicked() throws Exception {
        MvcResult loginResult = loginAsAdmin();
        String token = extractToken(loginResult);

        mockMvc.perform(get("/api/auth/me")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
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
        JsonNode offlinePayload = auditPayload("SESSION_FORCED_OFFLINE");
        Assertions.assertEquals("******", offlinePayload.path("sessionId").asText());
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
        Assertions.assertEquals(ADMIN_TENANT, current.path("activeTenantId").asText());
    }

  @Test
  void sessionsAllScopeShouldReturnTenantSessions() throws Exception {
    ensureTenantUser();
    MvcResult adminLogin = loginAsAdmin();
    MvcResult tenantLogin = loginAs(TENANT_USER, TENANT_USER_PASSWORD, TENANT_A);
    String adminToken = extractToken(adminLogin);
    String tenantToken = extractToken(tenantLogin);

    MvcResult response = mockMvc.perform(get("/api/auth/sessions?scope=all&page=1&size=50")
        .header("Authorization", "Bearer " + adminToken)
        .header("X-Tenant-Id", ADMIN_TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").isNumber())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.records").isArray())
        .andReturn();
    JsonNode sessions = pageRecords(response);
    Assertions.assertTrue(sessions.size() >= 2, "all scope should include visible active sessions");
    JsonNode adminSession = sessionByTokenFromPage(response, adminToken);
    JsonNode tenantSession = sessionByTokenFromPage(response, tenantToken);
    Assertions.assertEquals(ADMIN_TENANT, adminSession.path("tenantId").asText());
    Assertions.assertEquals(ADMIN_TENANT, adminSession.path("activeTenantId").asText());
    Assertions.assertEquals(TENANT_A, tenantSession.path("tenantId").asText());
    Assertions.assertEquals(TENANT_A, tenantSession.path("activeTenantId").asText());
    for (JsonNode session : sessions) {
      Assertions.assertTrue(session.path("active").asBoolean(), "all scope should only return active sessions");
    }
  }

  @Test
  void sessionsAllScopePaginationShouldRespectPageAndSize() throws Exception {
    MvcResult loginResult = loginAsAdmin();
    String adminToken = extractToken(loginResult);

    mockMvc.perform(get("/api/auth/sessions?scope=all&page=1&size=1")
        .header("Authorization", "Bearer " + adminToken)
        .header("X-Tenant-Id", ADMIN_TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(1))
        .andExpect(jsonPath("$.data.size").value(1))
        .andExpect(jsonPath("$.data.records.length()").value(1));

    mockMvc.perform(get("/api/auth/sessions?scope=all&page=2&size=1")
        .header("Authorization", "Bearer " + adminToken)
        .header("X-Tenant-Id", ADMIN_TENANT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.page").value(2))
        .andExpect(jsonPath("$.data.size").value(1));
  }

  @Test
  void forceOfflineNonexistentSessionShouldFail() throws Exception {
        MvcResult loginResult = loginAsAdmin();
        String token = extractToken(loginResult);

        mockMvc.perform(post("/api/auth/sessions/{sessionId}/offline", "nonexistent-session-uuid")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Tenant-Id", ADMIN_TENANT))
                .andExpect(status().isNotFound())
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

  private JsonNode pageRecords(MvcResult result) throws Exception {
    JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    JsonNode records = data.path("records");
    Assertions.assertTrue(records.isArray(), "page response data.records should be an array");
    return records;
  }

  private JsonNode sessionByTokenFromPage(MvcResult result, String token) throws Exception {
    for (JsonNode session : pageRecords(result)) {
      if (token.equals(session.path("sessionId").asText())) {
        return session;
      }
    }
    throw new AssertionError("session not found in page: " + token);
  }

    private JsonNode sessionByToken(MvcResult result, String token) throws Exception {
        for (JsonNode session : dataArray(result)) {
            if (token.equals(session.path("sessionId").asText())) {
                return session;
            }
        }
        throw new AssertionError("session not found: " + token);
    }

    private JsonNode logPayload(String eventType) throws Exception {
        List<String> payloads = jdbcTemplate.queryForList(
                """
                SELECT payload_json
                FROM sys_log
                WHERE event_type = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class,
                eventType
        );
        Assertions.assertFalse(payloads.isEmpty(), "missing log event " + eventType);
        return objectMapper.readTree(payloads.get(0));
    }

  private JsonNode latestLogPayload(String eventType, String requestId) throws Exception {
        List<String> payloads = jdbcTemplate.queryForList(
                """
                SELECT payload_json
                FROM sys_log
                WHERE event_type = ? AND request_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class,
                eventType,
                requestId
        );
        Assertions.assertFalse(payloads.isEmpty(), "missing log event " + eventType + " for request " + requestId);
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

    private JsonNode auditPayload(String eventType) throws Exception {
        List<String> payloads = jdbcTemplate.queryForList(
                """
                SELECT payload_json
                FROM sys_audit_log
                WHERE event_type = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class,
                eventType
        );
        Assertions.assertFalse(payloads.isEmpty(), "missing audit event " + eventType);
        return objectMapper.readTree(payloads.get(0));
    }

    private JsonNode latestAuditPayload(String eventType, String requestId) throws Exception {
        List<String> payloads = jdbcTemplate.queryForList(
                """
                SELECT payload_json
                FROM sys_audit_log
                WHERE event_type = ? AND request_id = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class,
                eventType,
                requestId
        );
        Assertions.assertFalse(payloads.isEmpty(), "missing audit event " + eventType + " for request " + requestId);
        return objectMapper.readTree(payloads.get(0));
    }

}
