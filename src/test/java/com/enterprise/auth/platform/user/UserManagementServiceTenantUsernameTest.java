package com.enterprise.auth.platform.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.user.interfaces.CreateUserRequest;
import com.enterprise.auth.platform.modules.user.application.UserManagementService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
class UserManagementServiceTenantUsernameTest {

    private static final String USERNAME_PREFIX = "tenant_username_ut_";

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username LIKE ?", USERNAME_PREFIX + "%");
    }

    @Test
    void createUserShouldRejectDuplicateUsernameAcrossTenants() {
        String username = USERNAME_PREFIX + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        insertUser("platform", username);

        assertThatThrownBy(() -> userManagementService.createUser(
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
        )).isInstanceOfSatisfying(BusinessException.class, ex -> {
            assertThat(ex.code()).isEqualTo("BUSINESS_ERROR");
            assertThat(ex.getMessage()).isNotBlank();
        });
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
            assertThat(ex.getMessage()).isNotBlank();
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
                passwordHasher.hash("UserTest@123"),
                1,
                1,
                "test",
                "test"
        );
    }
}
