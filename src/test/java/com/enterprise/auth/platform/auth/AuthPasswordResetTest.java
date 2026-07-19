package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.modules.auth.application.CaptchaService;
import com.enterprise.auth.platform.modules.auth.application.PasswordResetNotificationService;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest(properties = {
        "app.security.password-reset.username-max-requests=1",
        "app.security.password-reset.ip-max-requests=20",
        "app.security.notification.channel=log",
        "app.outbox.payload-secret-key=test-outbox-payload-secret-key-32-chars"
})
@AutoConfigureMockMvc
class AuthPasswordResetTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String USERNAME = "pwdresetut";
    private static final String EMAIL = "pwd-reset.ut@example.com";
    private static final String WRONG_EMAIL = "pwd-reset-wrong.ut@example.com";
    private static final String MISSING_USERNAME = "pwdmissut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private CaptchaService captchaService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private PasswordResetNotificationService passwordResetNotificationService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.doAnswer(invocation -> null).when(captchaService).secondaryVerify(org.mockito.ArgumentMatchers.anyString());
        cleanup();
        insertResetUser();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void passwordResetRequestShouldNotRevealAccountExistence() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(USERNAME, TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.result").value("EMAIL_SENT"))
                .andExpect(jsonPath("$.data.message").value("如果账号存在且邮箱匹配，将会收到密码重置邮件"))
                .andExpect(jsonPath("$.data.token").doesNotExist());

        mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(MISSING_USERNAME, TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.result").value("EMAIL_SENT"))
                .andExpect(jsonPath("$.data.message").value("如果账号存在且邮箱匹配，将会收到密码重置邮件"))
                .andExpect(jsonPath("$.data.token").doesNotExist());

        Integer existingTokenCount = tokenCount(USERNAME);
        Integer missingTokenCount = tokenCount(MISSING_USERNAME);
        String storedTokenHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM sys_password_reset_token WHERE tenant_id = ? AND username = ? AND deleted = 0",
                String.class,
                TENANT_ID,
                USERNAME
        );
        assertThat(existingTokenCount).isEqualTo(1);
        assertThat(missingTokenCount).isZero();
        assertThat(storedTokenHash).hasSize(64).matches("[0-9a-f]{64}");

        var resetLink = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(passwordResetNotificationService, org.mockito.Mockito.timeout(5000)).sendPasswordResetLink(
                org.mockito.ArgumentMatchers.eq(TENANT_ID),
                org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq(USERNAME),
                resetLink.capture()
        );
        assertThat(resetLink.getValue())
                .startsWith("http://localhost:5777/#/reset-password?token=");
        String storedOutboxPayload = jdbcTemplate.queryForObject(
                """
                SELECT payload_json
                FROM sys_outbox_event
                WHERE tenant_id = ? AND event_type = 'PASSWORD_RESET_MAIL'
                ORDER BY id DESC
                LIMIT 1
                """,
                String.class,
                TENANT_ID
        );
        assertThat(storedOutboxPayload).doesNotContain(resetLink.getValue());
    }

    @Test
    void passwordResetRequestShouldRequireMatchingEmail() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(USERNAME, TENANT_ID, WRONG_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.result").value("EMAIL_SENT"))
                .andExpect(jsonPath("$.data.message").value("如果账号存在且邮箱匹配，将会收到密码重置邮件"))
                .andExpect(jsonPath("$.data.token").doesNotExist());

        assertThat(tokenCount(USERNAME)).isZero();
    }

    @Test
    void passwordResetRequestShouldLimitRepeatedRequestsByUsername() throws Exception {
        mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(USERNAME, TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/auth/password/reset/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestPayload(USERNAME, TENANT_ID)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("请求过于频繁，请稍后再试"));

        assertThat(tokenCount(USERNAME)).isEqualTo(1);
    }

    @Test
    void passwordResetConfirmShouldConsumeTokenAndInvalidateOldPassword() throws Exception {
        String rawToken = "reset-confirm-token-ut";
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?",
                Long.class,
                TENANT_ID,
                USERNAME
        );
        jdbcTemplate.update(
                """
                INSERT INTO sys_password_reset_token (
                    tenant_id, user_id, username, token_hash, expires_at, request_ip,
                    created_by, updated_by, deleted
                ) VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE), ?, ?, ?, 0)
                """,
                TENANT_ID,
                userId,
                USERNAME,
                sha256(rawToken),
                "127.0.0.1",
                "test",
                "test"
        );

        mockMvc.perform(post("/api/auth/password/reset/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenPayload(rawToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.username").value(USERNAME))
                .andExpect(jsonPath("$.data.passwordPolicy.passwordMinLength").isNumber())
                .andExpect(jsonPath("$.data.passwordPolicy.passwordMaxLength").isNumber())
                .andExpect(jsonPath("$.data.passwordPolicy.passwordRequireLetter").isBoolean())
                .andExpect(jsonPath("$.data.passwordPolicy.passwordRequireNumber").isBoolean())
                .andExpect(jsonPath("$.data.passwordPolicy.passwordRequireSpecial").isBoolean());

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(rawToken, "ResetNew@123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("密码已重置，请使用新密码登录"));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE tenant_id = ? AND username = ?",
                String.class,
                TENANT_ID,
                USERNAME
        );
        Integer sessionVersion = jdbcTemplate.queryForObject(
                "SELECT session_version FROM sys_user WHERE tenant_id = ? AND username = ?",
                Integer.class,
                TENANT_ID,
                USERNAME
        );
        Integer mustChangePassword = jdbcTemplate.queryForObject(
                "SELECT must_change_password + 0 FROM sys_user WHERE tenant_id = ? AND username = ?",
                Integer.class,
                TENANT_ID,
                USERNAME
        );
        assertThat(passwordHasher.matches("ResetNew@123", passwordHash)).isTrue();
        assertThat(passwordHasher.matches("ResetOld@123", passwordHash)).isFalse();
        assertThat(sessionVersion).isEqualTo(2);
        assertThat(mustChangePassword).isZero();

        Integer usedTokenCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_password_reset_token WHERE tenant_id = ? AND username = ? AND used_at IS NOT NULL AND revoked_at IS NULL AND deleted = 0",
                Integer.class,
                TENANT_ID,
                USERNAME
        );
        assertThat(usedTokenCount).isEqualTo(1);

        mockMvc.perform(post("/api/auth/password/reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmPayload(rawToken, "ResetAgain@123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RESET_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("重置链接无效、已使用或已过期"));
    }

    private void insertResetUser() {
        jdbcTemplate.update(
                """
                INSERT INTO sys_user (
                    tenant_id, dept_id, username, display_name, email, password_hash,
                    enabled, session_version, created_by, updated_by, deleted, password_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW())
                """,
                TENANT_ID,
                1L,
                USERNAME,
                USERNAME,
                EMAIL,
                passwordHasher.hash("ResetOld@123"),
                1,
                1,
                "test",
                "test"
        );
    }

    private Integer tokenCount(String username) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_password_reset_token WHERE tenant_id = ? AND username = ? AND deleted = 0",
                Integer.class,
                TENANT_ID,
                username
        );
    }

    private void cleanup() {
        jdbcTemplate.update(
                "DELETE FROM sys_outbox_event WHERE tenant_id = ? AND event_type = 'PASSWORD_RESET_MAIL'",
                TENANT_ID
        );
        jdbcTemplate.update("DELETE FROM sys_password_reset_token WHERE tenant_id = ? AND username IN (?, ?)",
                TENANT_ID, USERNAME, MISSING_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?)",
                TENANT_ID, USERNAME, MISSING_USERNAME);
        jdbcTemplate.update("DELETE FROM sys_log WHERE operator IN (?, ?)",
                USERNAME, MISSING_USERNAME);
    }

    private String requestPayload(String username, String tenantId) {
        return requestPayload(username, tenantId, EMAIL);
    }

    private String requestPayload(String username, String tenantId, String email) {
        return """
                {
                  "username": "%s",
                  "email": "%s",
                  "tenantId": "%s",
                  "captchaId": "captcha-pwd-reset-ut"
                }
                """.formatted(username, email, tenantId);
    }

    private String tokenPayload(String token) {
        return """
                {
                  "token": "%s"
                }
                """.formatted(token);
    }

    private String confirmPayload(String token, String newPassword) {
        return """
                {
                  "token": "%s",
                  "newPassword": "%s"
                }
                """.formatted(token, newPassword);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
