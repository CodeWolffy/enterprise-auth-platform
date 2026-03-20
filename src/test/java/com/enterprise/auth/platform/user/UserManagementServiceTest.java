package com.enterprise.auth.platform.user;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.dto.CreateUserRequest;
import com.enterprise.auth.platform.user.dto.UpdateUserRequest;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.service.management.UserManagementService;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class UserManagementServiceTest {

    private static final String SCOPE_USER = "user_scope_user_ut";
    private static final String HIDDEN_USER = "user_hidden_user_ut";
    private static final String CREATED_USER = "user_created_hidden_dept_ut";

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?, ?)",
                "tenant-a",
                SCOPE_USER,
                HIDDEN_USER,
                CREATED_USER
        );
    }

    @Test
    void shouldRejectCreatingUserInHiddenDepartment() {
        TenantContext.setTenantId("tenant-a");
        Long scopeUserId = ensureUser(SCOPE_USER, 2L);
        authenticateScopedUser(scopeUserId, "user:write");

        assertThatThrownBy(() -> userManagementService.create(new CreateUserRequest(
                CREATED_USER,
                "越权创建用户",
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
        Long scopeUserId = ensureUser(SCOPE_USER, 2L);
        Long hiddenUserId = ensureUser(HIDDEN_USER, 3L);
        authenticateScopedUser(scopeUserId, "user:write");

        assertThatThrownBy(() -> userManagementService.update(hiddenUserId, new UpdateUserRequest(
                "隐藏用户",
                null,
                null,
                3L,
                true,
                null,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户");
    }

    private Long ensureUser(String username, Long deptId) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", username);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptId(deptId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setPasswordHash(passwordEncoder.encode("UserTest@123"));
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
                passwordEncoder.encode("UserTest@123"),
                true,
                Set.of(),
                Set.of(permission),
                Set.of(),
                DataScopeType.DEPT,
                1
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, principal.password(), principal.getAuthorities())
        );
    }
}
