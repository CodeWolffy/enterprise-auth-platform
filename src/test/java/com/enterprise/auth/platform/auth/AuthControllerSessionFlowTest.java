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
                passwordHasher.hash(TENANT_USER_PASSWORD),
                1,
                1,
                "test",
                "test"
        );
    }
}
