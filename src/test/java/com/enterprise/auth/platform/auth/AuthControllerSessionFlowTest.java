package com.enterprise.auth.platform.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "app.security.redis.session-enabled=false",
        "app.security.redis.captcha-enabled=false"
})
@AutoConfigureMockMvc
class AuthControllerSessionFlowTest {

    private static final String ADMIN_PASSWORD = "Admin@123456";
    private static final String CAPTCHA_ID = "captcha-ut";
    private static final String CAPTCHA_CODE = "2468";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CaptchaService captchaService;

    @MockBean
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
                Instant.now().plusSeconds(300),
                CAPTCHA_CODE
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
        sessions.clear();
    }

    @Test
    void loginShouldIssueCookieAndAuthorizeSessionEndpoints() throws Exception {
        mockMvc.perform(get("/api/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.captchaId").value(CAPTCHA_ID))
                .andExpect(jsonPath("$.data.previewCode").value(CAPTCHA_CODE));

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "%s",
                                  "captchaId": "%s",
                                  "captchaCode": "%s",
                                  "tenantId": "platform",
                                  "device": "test-browser"
                                }
                                """.formatted(ADMIN_PASSWORD, CAPTCHA_ID, CAPTCHA_CODE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString(AuthCookieConstants.SESSION_COOKIE + "=")))
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String sessionId = loginBody.path("data").path("sessionId").asText();
        Cookie sessionCookie = new Cookie(AuthCookieConstants.SESSION_COOKIE, cookieValue(loginResult, AuthCookieConstants.SESSION_COOKIE));

        mockMvc.perform(get("/api/auth/me")
                        .cookie(sessionCookie)
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.permissions[?(@=='tenant:read')]").exists());

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

    private String cookieValue(MvcResult result, String cookieName) {
        String headerValue = result.getResponse().getHeader("Set-Cookie");
        if (headerValue == null) {
            throw new IllegalStateException("Missing Set-Cookie header");
        }
        for (String part : headerValue.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(cookieName + "=")) {
                return trimmed.substring((cookieName + "=").length());
            }
        }
        throw new IllegalStateException("Missing cookie " + cookieName);
    }
}
