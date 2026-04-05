package com.enterprise.auth.platform.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.auth.controller.AuthController;
import com.enterprise.auth.platform.auth.model.UserSession;
import com.enterprise.auth.platform.auth.service.CaptchaService;
import com.enterprise.auth.platform.auth.store.SessionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "app.security.redis.session-enabled=false",
        "app.security.redis.captcha-enabled=false"
})
@AutoConfigureMockMvc
class AuthControllerSessionFlowTest {

    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final String TENANT_USER = "session_flow_tenant_user";
    private static final String TENANT_USER_PASSWORD = "Tenant@123456";
    private static final String CAPTCHA_ID = "captcha-ut";
    private static final String CAPTCHA_CODE = "24682";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CaptchaService captchaService;

    @MockitoBean
    private SessionStore sessionStore;

    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
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
                passwordEncoder.encode(ADMIN_PASSWORD),
                "platform",
                "admin"
        );

        sessions.clear();
        when(captchaService.create()).thenReturn(new CaptchaService.CaptchaChallenge(
                CAPTCHA_ID,
                Instant.now().plusSeconds(60),
                new byte[] {1, 2, 3, 4}
        ));
        doAnswer(invocation -> null).when(captchaService).validate(anyString(), anyString());
        doAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            sessions.put(session.sessionId(), session);
            return null;
        }).when(sessionStore).save(any(UserSession.class));
        when(sessionStore.findBySessionId(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(sessions.get(invocation.getArgument(0))));
        when(sessionStore.findByUserId(anyLong())).thenAnswer(invocation -> sessions.values().stream()
                .filter(session -> session.userId().equals(invocation.getArgument(0)))
                .sorted(Comparator.comparing(UserSession::issuedAt).reversed())
                .toList());
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            UserSession session = sessions.get(sessionId);
            if (session != null) {
                sessions.put(sessionId, session.deactivate(Instant.now()));
            }
            return null;
        }).when(sessionStore).deactivate(anyString());
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(0);
            UserSession session = sessions.get(sessionId);
            if (session != null) {
                sessions.put(sessionId, session.touch(Instant.now()));
            }
            return null;
        }).when(sessionStore).touch(anyString());
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
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", TENANT_USER);
        sessions.clear();
    }

    @Test
    void 登录应颁发Cookie并授权会话端点() throws Exception {
        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string(AuthController.CAPTCHA_ID_HEADER, CAPTCHA_ID))
                .andExpect(header().exists(AuthController.CAPTCHA_EXPIRES_AT_HEADER))
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));

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
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String sessionId = loginBody.path("data").path("sessionId").asText();
        Cookie sessionCookie = loginResult.getResponse().getCookie(AuthCookieConstants.SESSION_COOKIE);
        Assertions.assertNotNull(sessionCookie, "缺少会话 Cookie");

        mockMvc.perform(get("/api/auth/me")
                        .cookie(sessionCookie)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.grants[?(@=='tenant:read')]").exists());

        mockMvc.perform(get("/api/auth/sessions")
                        .cookie(sessionCookie)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.data[0].tenantId").value("platform"));

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .cookie(sessionCookie)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(get("/api/auth/me")
                        .cookie(sessionCookie)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void 登录应忽略已有Cookie并按用户名解析租户() throws Exception {
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

        Cookie adminSessionCookie = adminLoginResult.getResponse().getCookie(AuthCookieConstants.SESSION_COOKIE);
        Assertions.assertNotNull(adminSessionCookie, "Missing admin session cookie");

        mockMvc.perform(post("/api/auth/login")
                        .cookie(adminSessionCookie)
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

    private void ensureTenantUser() {
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
                passwordEncoder.encode(TENANT_USER_PASSWORD),
                1,
                1,
                "test",
                "test"
        );
    }
}
