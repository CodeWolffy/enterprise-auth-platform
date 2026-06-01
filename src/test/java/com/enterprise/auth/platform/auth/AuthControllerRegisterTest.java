package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.registration.max-attempts-per-user-ip=2",
        "app.registration.max-attempts-per-ip=100"
})
@AutoConfigureMockMvc
class AuthControllerRegisterTest {

    private static final String PLATFORM_TENANT = "platform";
    private static final String DEFAULT_TENANT = "tenant-a";
    private static final String DEFAULT_ROLE_CODE = "REGISTER_BASE_TEST";
    private static final String USERNAME_PREFIX = "register_api_ut_";
    private static final String CONFIG_KEY_DEFAULT_TENANT = "registration.default_tenant_id";
    private static final String CONFIG_KEY_DEFAULT_ROLE_CODES = "registration.default_role_codes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    private ConfigSnapshot previousDefaultTenantConfig;
    private ConfigSnapshot previousDefaultRoleCodesConfig;

    @BeforeEach
    void setUp() {
        ensureDefaultRole();

        previousDefaultTenantConfig = snapshotConfig(CONFIG_KEY_DEFAULT_TENANT);
        previousDefaultRoleCodesConfig = snapshotConfig(CONFIG_KEY_DEFAULT_ROLE_CODES);

        upsertConfig(CONFIG_KEY_DEFAULT_TENANT, DEFAULT_TENANT, "注册默认租户");
        upsertConfig(CONFIG_KEY_DEFAULT_ROLE_CODES, DEFAULT_ROLE_CODE, "注册默认角色");
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                """
                DELETE ur FROM sys_user_role ur
                JOIN sys_user u ON u.id = ur.user_id
                WHERE u.username LIKE ?
                """,
                USERNAME_PREFIX + "%"
        );
        jdbcTemplate.update(
                """
                DELETE ur FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id
                WHERE r.tenant_id = ? AND r.role_code = ?
                """,
                DEFAULT_TENANT,
                DEFAULT_ROLE_CODE
        );
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", USERNAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", DEFAULT_TENANT, DEFAULT_ROLE_CODE);

        restoreConfig(CONFIG_KEY_DEFAULT_TENANT, previousDefaultTenantConfig);
        restoreConfig(CONFIG_KEY_DEFAULT_ROLE_CODES, previousDefaultRoleCodesConfig);
    }

    @Test
    void registerShouldCreateUserInDefaultTenant() throws Exception {
        String username = nextUsername();
        String rawPassword = "Register123";
        String mobile = "13800001111";
        String email = "register.ut@example.com";

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(username, "注册测试用户", rawPassword, mobile, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.tenantId").value(DEFAULT_TENANT))
                .andExpect(jsonPath("$.data.mobile").value(mobile))
                .andExpect(jsonPath("$.data.email").value(email));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE username = ? AND tenant_id = ? AND deleted = 0",
                Integer.class,
                username,
                DEFAULT_TENANT
        );
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE username = ? AND deleted = 0",
                String.class,
                username
        );
        Integer roleCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id
                JOIN sys_user u ON u.id = ur.user_id
                WHERE ur.tenant_id = ?
                  AND u.username = ?
                  AND u.deleted = 0
                  AND r.tenant_id = ?
                  AND r.role_code = ?
                  AND r.deleted = 0
                """,
                Integer.class,
                DEFAULT_TENANT,
                username,
                DEFAULT_TENANT,
                DEFAULT_ROLE_CODE
        );

        assertThat(count).isEqualTo(1);
        assertThat(passwordHash).isNotBlank();
        assertThat(passwordHash).isNotEqualTo(rawPassword);
        assertThat(passwordHasher.matches(rawPassword, passwordHash)).isTrue();
        assertThat(roleCount).isEqualTo(1);
    }

    @Test
    void registerOptionsShouldExposeDefaultTenant() throws Exception {
        mockMvc.perform(get("/api/auth/register/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.defaultTenantId").value(DEFAULT_TENANT))
                .andExpect(jsonPath("$.data.defaultRoleCodes[0]").value(DEFAULT_ROLE_CODE));
    }

    @Test
    void registerShouldRejectWeakPassword() throws Exception {
        String username = nextUsername();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(username, "弱密码用户", "weakpass", "13800002222", "weak.ut@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INVALID"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE username = ? AND deleted = 0",
                Integer.class,
                username
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    void registerShouldRejectDuplicateUsername() throws Exception {
        String username = nextUsername();
        String payload = registerPayload(username, "重复用户名用户", "Repeat123", "13800003333", "repeat.ut@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    void registerShouldRejectDuplicateUsernameAcrossTenants() throws Exception {
        String username = nextUsername();
        insertUser("platform", username);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerPayload(username, "跨租户重复用户", "Repeat123", "13800005555", "repeat.cross.ut@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    void registerShouldLimitAttemptsByIpAndUsername() throws Exception {
        String username = nextUsername();
        String weakPayload = registerPayload(username, "限频用户", "weakpass", "13800004444", "limit.ut@example.com");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weakPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INVALID"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weakPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_INVALID"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weakPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REGISTER_RATE_LIMITED"));
    }

    private String nextUsername() {
        return USERNAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void ensureDefaultRole() {
        jdbcTemplate.update(
                """
                DELETE ur FROM sys_user_role ur
                JOIN sys_role r ON r.id = ur.role_id
                WHERE r.tenant_id = ? AND r.role_code = ?
                """,
                DEFAULT_TENANT,
                DEFAULT_ROLE_CODE
        );
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", DEFAULT_TENANT, DEFAULT_ROLE_CODE);
        jdbcTemplate.update(
                """
                INSERT INTO sys_role (tenant_id, role_code, role_name, data_scope_type, role_desc, created_by, updated_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0)
                """,
                DEFAULT_TENANT,
                DEFAULT_ROLE_CODE,
                "注册默认角色测试",
                "SELF",
                "注册自动分配默认角色测试数据",
                "test",
                "test"
        );
    }

    private ConfigSnapshot snapshotConfig(String configKey) {
        List<ConfigSnapshot> result = jdbcTemplate.query(
                """
                SELECT config_value, config_name
                FROM sys_config
                WHERE tenant_id = ? AND config_key = ? AND deleted = 0
                ORDER BY id DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new ConfigSnapshot(true, rs.getString("config_value"), rs.getString("config_name")),
                PLATFORM_TENANT,
                configKey
        );
        if (result.isEmpty()) {
            return ConfigSnapshot.absent();
        }
        return result.get(0);
    }

    private void restoreConfig(String configKey, ConfigSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            jdbcTemplate.update(
                    "DELETE FROM sys_config WHERE tenant_id = ? AND config_key = ?",
                    PLATFORM_TENANT,
                    configKey
            );
            return;
        }
        upsertConfig(configKey, snapshot.value(), snapshot.configName());
    }

    private void upsertConfig(String configKey, String configValue, String configName) {
        String resolvedName = (configName == null || configName.isBlank()) ? configKey : configName;
        jdbcTemplate.update(
                """
                INSERT INTO sys_config (tenant_id, config_key, config_name, config_value, created_by, updated_by, deleted)
                VALUES (?, ?, ?, ?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    config_name = VALUES(config_name),
                    config_value = VALUES(config_value),
                    updated_by = VALUES(updated_by),
                    deleted = 0
                """,
                PLATFORM_TENANT,
                configKey,
                resolvedName,
                configValue,
                "test",
                "test"
        );
    }

    private void insertUser(String tenantId, String username) {
        jdbcTemplate.update(
                """
                INSERT INTO sys_user (
                    tenant_id, dept_id, username, display_name, password_hash,
                    enabled, session_version, created_by, updated_by, deleted, password_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW())
                """,
                tenantId,
                1L,
                username,
                username,
                passwordHasher.hash("Repeat123"),
                1,
                1,
                "test",
                "test"
        );
    }

    private String registerPayload(String username, String displayName, String password, String mobile, String email) {
        return """
                {
                  "username": "%s",
                  "displayName": "%s",
                  "password": "%s",
                  "mobile": "%s",
                  "email": "%s"
                }
                """.formatted(username, displayName, password, mobile, email);
    }

    private record ConfigSnapshot(boolean exists, String value, String configName) {

        private static ConfigSnapshot absent() {
            return new ConfigSnapshot(false, null, null);
        }
    }
}
