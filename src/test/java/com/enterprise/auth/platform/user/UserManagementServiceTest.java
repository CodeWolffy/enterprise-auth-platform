package com.enterprise.auth.platform.user;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.dto.model.DataScopeType;
import com.enterprise.auth.platform.dao.entity.SysUserEntity;
import com.enterprise.auth.platform.dao.mapper.SysUserMapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.dto.req.CreateUserRequest;
import com.enterprise.auth.platform.dto.req.CreateUserRequest;
import com.enterprise.auth.platform.dto.model.UserAccount;
import com.enterprise.auth.platform.service.UserManagementService;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.security.PasswordHasher;

@SpringBootTest
class UserManagementServiceTest {

    private static final String SCOPE_USER = "user_scope_user_ut";
    private static final String HIDDEN_USER = "user_hidden_user_ut";
    private static final String CREATED_USER = "user_created_hidden_dept_ut";
    private static final String CROSS_TENANT_USER = "user_cross_tenant_ut";

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        clear();
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?, ?, ?)",
                "tenant-a",
                SCOPE_USER,
                HIDDEN_USER,
                CREATED_USER,
                CROSS_TENANT_USER
        );
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE tenant_id = ? AND username = ?",
                "platform",
                CROSS_TENANT_USER
        );
    }

    @Test
    void shouldRejectCreatingUserInHiddenDepartment() {
        TenantContext.setTenantId("tenant-a");
        Long scopeUserId = ensureUser("tenant-a", SCOPE_USER, 2L);
        authenticateScopedUser(scopeUserId, "user:write");

        assertThatThrownBy(() -> userManagementService.create(new CreateUserRequest(
                CREATED_USER,
                "Blocked Dept User",
                null,
                null,
                "UserTest@123",
                3L,
                true,
                Set.of()
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("部门");
    }

    @Test
    void shouldRejectUpdatingHiddenUser() {
        TenantContext.setTenantId("tenant-a");
        Long scopeUserId = ensureUser("tenant-a", SCOPE_USER, 2L);
        Long hiddenUserId = ensureUser("tenant-a", HIDDEN_USER, 3L);
        authenticateScopedUser(scopeUserId, "user:write");

        assertThatThrownBy(() -> userManagementService.update(hiddenUserId, new CreateUserRequest(
                null,
                "Hidden User",
                null,
                null,
                null,
                3L,
                true,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户");
    }

    @Test
    void shouldRejectCreatingUserWhenUsernameExistsInAnotherTenant() {
        TenantContext.setTenantId("tenant-a");
        ensureUser("platform", CROSS_TENANT_USER, 1L);
        Long scopeUserId = ensureUser("tenant-a", SCOPE_USER, 2L);
        authenticateScopedUser(scopeUserId, "user:write");

        assertThatThrownBy(() -> userManagementService.create(new CreateUserRequest(
                CROSS_TENANT_USER,
                "Cross Tenant User",
                null,
                null,
                "UserTest@123",
                2L,
                true,
                Set.of()
        )))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.code()).isEqualTo("BUSINESS_ERROR");
                    assertThat(ex.getMessage()).isNotBlank();
                });
    }

    private Long ensureUser(String tenantId, String username, Long deptId) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", tenantId, username);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(tenantId);
        entity.setDeptId(deptId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setPasswordHash(passwordHasher.hash("UserTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }

    private void authenticateScopedUser(Long userId, String permission) {
        UserAccount principal = new UserAccount(
                userId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("UserTest@123"),
                true,
                Set.of(),
                Set.of(permission),
                Set.of(),
                DataScopeType.DEPT,
                1
        );
        bind(principal);
    }
}
