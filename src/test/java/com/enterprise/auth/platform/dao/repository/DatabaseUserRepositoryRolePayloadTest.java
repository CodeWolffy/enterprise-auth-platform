package com.enterprise.auth.platform.dao.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.role.application.RolePayloadCodec;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import com.enterprise.auth.platform.modules.user.infrastructure.repository.DatabaseUserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
class DatabaseUserRepositoryRolePayloadTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String USERNAME_PREFIX = "role_payload_ut_";
    private static final String ROLE_CODE_PREFIX = "ROLE_PAYLOAD_UT_";

    @Autowired
    private DatabaseUserRepository databaseUserRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private RolePayloadCodec rolePayloadCodec;

    private String username;
    private String roleCode;
    private String permissionCode;
    private Long userId;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        username = USERNAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        roleCode = ROLE_CODE_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        permissionCode = "perm_payload_ut_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        jdbcTemplate.update(
                """
                INSERT INTO sys_role (
                    tenant_id, role_code, role_name, data_scope_type, role_desc,
                    data_scope_value_json, created_by, updated_by, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                TENANT_ID,
                roleCode,
                "Role Payload Test",
                "CUSTOM",
                "payload test role",
                rolePayloadCodec.writeDeptIds(Set.of(2L, 3L)),
                "test",
                "test"
        );
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ? AND deleted = 0",
                Long.class,
                TENANT_ID,
                roleCode
        );
        List<Long> menuIds = jdbcTemplate.queryForList(
                """
                SELECT id FROM sys_menu
                WHERE deleted = 0
                  AND type = '1'
                  AND permission IN ('upms:sysuser:get', 'upms:operationlog:get')
                """,
                Long.class
        );
        for (Long menuId : menuIds) {
            jdbcTemplate.update(
                    """
                    INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id, created_at)
                    VALUES (?, ?, ?, NOW())
                    """,
                    TENANT_ID,
                    roleId,
                    menuId
            );
        }
        jdbcTemplate.update(
                """
                INSERT INTO sys_user (
                    tenant_id, dept_id, username, display_name, password_hash,
                    enabled, session_version, created_by, updated_by, deleted, password_updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NOW())
                """,
                TENANT_ID,
                2L,
                username,
                "Role Payload User",
                passwordHasher.hash("UserTest@123"),
                1,
                1,
                "test",
                "test"
        );
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                Long.class,
                TENANT_ID,
                username
        );

        jdbcTemplate.update(
                "INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                TENANT_ID,
                userId,
                roleId
        );
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", TENANT_ID, userId);
        }
        if (username != null) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", TENANT_ID, username);
        }
        if (roleCode != null) {
            Long roleId = jdbcTemplate.query(
                            "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                            (rs, rowNum) -> rs.getLong(1),
                            TENANT_ID,
                            roleCode
                    ).stream()
                    .findFirst()
                    .orElse(null);
            if (roleId != null) {
                jdbcTemplate.update("DELETE FROM sys_role_menu WHERE tenant_id = ? AND role_id = ?", TENANT_ID, roleId);
            }
            jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", TENANT_ID, roleCode);
        }
    }

    @Test
    void findByUsernameShouldLoadPermissionsAndCustomScopeFromRolePayload() {
        Optional<AuthenticationUser> result = databaseUserRepository.findByUsername(TENANT_ID, username);

        assertThat(result).isPresent();
        var user = result.orElseThrow();
        assertThat(user.roles()).contains(roleCode);
        assertThat(user.permissions()).containsExactlyInAnyOrder("upms:sysuser:get", "upms:operationlog:get");
        assertThat(user.permissions()).doesNotContain("payload:extra");
        assertThat(user.dataScopeType()).isEqualTo(DataScopeType.CUSTOM);
        assertThat(user.customDeptIds()).containsExactlyInAnyOrder(2L, 3L);
    }
}
