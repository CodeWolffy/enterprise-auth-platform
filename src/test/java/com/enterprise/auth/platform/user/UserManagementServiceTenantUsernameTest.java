package com.enterprise.auth.platform.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.user.dto.CreateUserRequest;
import com.enterprise.auth.platform.user.model.UserSummary;
import com.enterprise.auth.platform.user.service.management.UserManagementService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class UserManagementServiceTenantUsernameTest {

    private static final String USERNAME_PREFIX = "tenant_username_ut_";

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", USERNAME_PREFIX + "%");
    }

    @Test
    void createUserShouldAllowSameUsernameInDifferentTenants() {
        String username = USERNAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        insertUser("platform", username);

        UserSummary created = userManagementService.createUser(
                "tenant-a",
                new CreateUserRequest(
                        username,
                        "Cross Tenant User",
                        null,
                        null,
                        "UserTest@123",
                        null,
                        true,
                        Set.of()
                ),
                "test"
        );

        Integer platformCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                Integer.class,
                "platform",
                username
        );
        Integer tenantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                Integer.class,
                "tenant-a",
                username
        );

        assertThat(created.tenantId()).isEqualTo("tenant-a");
        assertThat(created.username()).isEqualTo(username);
        assertThat(platformCount).isEqualTo(1);
        assertThat(tenantCount).isEqualTo(1);
    }

    @Test
    void createUserShouldRejectDuplicateUsernameInSameTenant() {
        String username = USERNAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        insertUser("tenant-a", username);

        assertThatThrownBy(() -> userManagementService.createUser(
                "tenant-a",
                new CreateUserRequest(
                        username,
                        "Duplicate Tenant User",
                        null,
                        null,
                        "UserTest@123",
                        null,
                        true,
                        Set.of()
                ),
                "test"
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.code()).isEqualTo("BUSINESS_ERROR");
            assertThat(ex.getMessage()).contains("鐢ㄦ埛鍚");
        });
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
                passwordEncoder.encode("UserTest@123"),
                1,
                1,
                "test",
                "test"
        );
    }
}
