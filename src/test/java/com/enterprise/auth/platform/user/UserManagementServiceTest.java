package com.enterprise.auth.platform.user;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.user.interfaces.CreateUserRequest;
import com.enterprise.auth.platform.modules.user.interfaces.CreateUserRequest;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.UserManagementService;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;

@SpringBootTest
class UserManagementServiceTest {

    private static final String SCOPE_USER = "user_scope_user_ut";
    private static final String HIDDEN_USER = "user_hidden_user_ut";
    private static final String CREATED_USER = "user_created_hidden_dept_ut";
    private static final String CROSS_TENANT_USER = "user_cross_tenant_ut";
    private static final String ADMIN_ROLE = "USER_SELF_ADMIN_UT";
    private static final String STAFF_ROLE = "USER_SELF_STAFF_UT";

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        clear();
        jdbcTemplate.update(
                "DELETE FROM sys_user_role WHERE tenant_id IN (?, ?) AND (user_id IN (SELECT id FROM sys_user WHERE username IN (?, ?, ?, ?)) OR role_id IN (SELECT id FROM sys_role WHERE role_code IN (?, ?)))",
                "tenant-a",
                "platform",
                SCOPE_USER,
                HIDDEN_USER,
                CREATED_USER,
                CROSS_TENANT_USER,
                ADMIN_ROLE,
                STAFF_ROLE
        );
        jdbcTemplate.update(
                "DELETE FROM sys_role WHERE tenant_id IN (?, ?) AND role_code IN (?, ?)",
                "tenant-a",
                "platform",
                ADMIN_ROLE,
                STAFF_ROLE
        );
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
        authenticateScopedUser(scopeUserId, "upms:sysuser:edit");

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
        authenticateScopedUser(scopeUserId, "upms:sysuser:edit");

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
        authenticateScopedUser(scopeUserId, "upms:sysuser:edit");

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

    @Test
    void shouldRejectDeletingCurrentUser() {
        TenantContext.setTenantId("tenant-a");
        Long currentUserId = ensureUser("tenant-a", SCOPE_USER, 2L);
        authenticateScopedUser(currentUserId, "upms:sysuser:edit");

        assertThatThrownBy(() -> userManagementService.delete(currentUserId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录用户");
    }

    @Test
    void shouldRejectDisablingCurrentUser() {
        TenantContext.setTenantId("tenant-a");
        Long currentUserId = ensureUser("tenant-a", SCOPE_USER, 2L);
        authenticateScopedUser(currentUserId, "upms:sysuser:edit");

        assertThatThrownBy(() -> userManagementService.update(currentUserId, new CreateUserRequest(
                null,
                "Current User",
                null,
                null,
                null,
                2L,
                false,
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录用户");
    }

    @Test
    void shouldRejectRemovingAllRolesFromCurrentUser() {
        TenantContext.setTenantId("tenant-a");
        Long currentUserId = ensureUser("tenant-a", SCOPE_USER, 2L);
        ensureRole("tenant-a", ADMIN_ROLE);
        authenticateScopedUser(currentUserId, "upms:sysuser:edit");

        assertThatThrownBy(() -> userManagementService.assignRoles(currentUserId, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前登录用户");
    }

    @Test
    void platformAdminShouldAssignAndLoadRolesFromTargetTenant() {
        TenantContext.setGlobalScope("platform");
        Long tenantUserId = ensureUser("tenant-a", CROSS_TENANT_USER, 2L);
        Long tenantRoleId = ensureRole("tenant-a", STAFF_ROLE);
        Long platformRoleId = ensureRole("platform", STAFF_ROLE);
        authenticatePlatformAdmin();

        var summary = userManagementService.assignRoles(tenantUserId, Set.of(" " + STAFF_ROLE + " "));

        assertThat(summary.tenantId()).isEqualTo("tenant-a");
        assertThat(summary.roles()).contains(STAFF_ROLE);

        var assignedRoles = userManagementService.listAssignedRoles(tenantUserId);
        assertThat(assignedRoles).hasSize(1);
        assertThat(assignedRoles.get(0).id()).isEqualTo(tenantRoleId);
        assertThat(assignedRoles.get(0).tenantId()).isEqualTo("tenant-a");
        assertThat(assignedRoles).extracting(role -> role.id()).doesNotContain(platformRoleId);
    }

    @Test
    void platformAdminShouldCreateUserInRequestedTenant() {
        TenantContext.setGlobalScope("platform");
        ensureRole("tenant-a", STAFF_ROLE);
        authenticatePlatformAdmin();

        var summary = userManagementService.create(new CreateUserRequest(
                CREATED_USER,
                "Tenant A User",
                null,
                null,
                "UserTest@123",
                2L,
                true,
                Set.of(STAFF_ROLE),
                "tenant-a"
        ));

        assertThat(summary.tenantId()).isEqualTo("tenant-a");
        assertThat(summary.roles()).contains(STAFF_ROLE);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0",
                Long.class,
                "tenant-a",
                CREATED_USER
        );
        assertThat(count).isEqualTo(1L);
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

    private Long ensureRole(String tenantId, String roleCode) {
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", tenantId, roleCode);
        SysRoleEntity entity = new SysRoleEntity();
        entity.setTenantId(tenantId);
        entity.setRoleCode(roleCode);
        entity.setRoleName(roleCode);
        entity.setDataScopeType(DataScopeType.ALL.name());
        sysRoleMapper.insert(entity);
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

    private void authenticatePlatformAdmin() {
        UserAccount principal = new UserAccount(
                1L,
                "platform",
                "admin",
                passwordHasher.hash("UserTest@123"),
                true,
                Set.of("ADMIN"),
                Set.of("upms:sysuser:edit", "upms:sysuser:get"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
        bind(principal);
    }
}
