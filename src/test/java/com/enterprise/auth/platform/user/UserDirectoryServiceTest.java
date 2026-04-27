package com.enterprise.auth.platform.user;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.model.UserSummary;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.enterprise.auth.platform.user.service.UserDirectoryService;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.enterprise.auth.platform.security.PasswordHasher;

@SpringBootTest
class UserDirectoryServiceTest {

    private static final String PLATFORM_TEST_USER = "platform_user_ut";
    private static final String TENANT_TEST_USER = "tenant_user_ut";
    private static final String TENANT_OTHER_DEPT_USER = "tenant_other_dept_ut";
    private static final String TENANT_MANAGER_USER = "tenant_manager_ut";
    private static final String TENANT_CUSTOM_SCOPE_USER = "tenant_custom_scope_ut";
    private static final String TENANT_CHILD_DEPT_USER = "tenant_child_dept_ut";
    private static final String TENANT_CHILD_DEPT_CODE = "tenant_child_dept_ut";

    @Autowired
    private UserDirectoryService userDirectoryService;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long childDeptId;

    @BeforeEach
    void setUp() {
        childDeptId = ensureDept("tenant-a", TENANT_CHILD_DEPT_CODE, "租户A-测试子部门", 2L);
        ensureUser("platform", PLATFORM_TEST_USER, 1L);
        ensureUser("tenant-a", TENANT_TEST_USER, 2L);
        ensureUser("tenant-a", TENANT_OTHER_DEPT_USER, 3L);
        ensureUser("tenant-a", TENANT_MANAGER_USER, 2L);
        ensureUser("tenant-a", TENANT_CUSTOM_SCOPE_USER, 2L);
        ensureUser("tenant-a", TENANT_CHILD_DEPT_USER, childDeptId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        clear();
        deleteUser("platform", PLATFORM_TEST_USER);
        deleteUser("tenant-a", TENANT_TEST_USER);
        deleteUser("tenant-a", TENANT_OTHER_DEPT_USER);
        deleteUser("tenant-a", TENANT_MANAGER_USER);
        deleteUser("tenant-a", TENANT_CUSTOM_SCOPE_USER);
        deleteUser("tenant-a", TENANT_CHILD_DEPT_USER);
        deleteDept("tenant-a", TENANT_CHILD_DEPT_CODE);
    }

    @Test
    void listUsersShouldRespectCurrentTenant() {
        TenantContext.setTenantId("tenant-a");
        UserAccount auditor = userRepository.findByUsername("tenant-a", "auditor").orElseThrow();
        bind(auditor);
        List<UserSummary> tenantUsers = userDirectoryService.listUsers();
        assertThat(tenantUsers).extracting(UserSummary::tenantId).containsOnly("tenant-a");
        assertThat(tenantUsers).extracting(UserSummary::username).contains("auditor");
        assertThat(tenantUsers).extracting(UserSummary::username).contains(TENANT_TEST_USER);
        assertThat(tenantUsers).extracting(UserSummary::username).doesNotContain(PLATFORM_TEST_USER);
        assertThat(tenantUsers).extracting(UserSummary::username).doesNotContain(TENANT_OTHER_DEPT_USER);

        TenantContext.setTenantId("platform");
        UserAccount admin = userRepository.findByUsername("platform", "admin").orElseThrow();
        bind(admin);
        List<UserSummary> platformUsers = userDirectoryService.listUsers();
        assertThat(platformUsers).extracting(UserSummary::tenantId).containsOnly("platform");
        assertThat(platformUsers).extracting(UserSummary::username).contains(PLATFORM_TEST_USER);
        assertThat(platformUsers).extracting(UserSummary::username).doesNotContain(TENANT_TEST_USER);
    }

    @Test
    void listUsersShouldApplyDeptAndChildrenScope() {
        TenantContext.setTenantId("tenant-a");
        UserAccount manager = loadScopedUser(TENANT_MANAGER_USER, DataScopeType.DEPT_AND_CHILDREN, Set.of());
        bind(manager);

        List<UserSummary> users = userDirectoryService.listUsers();

        assertThat(users).extracting(UserSummary::username)
                .contains(TENANT_TEST_USER, TENANT_CHILD_DEPT_USER, TENANT_MANAGER_USER)
                .doesNotContain(TENANT_OTHER_DEPT_USER)
                .doesNotContain(PLATFORM_TEST_USER);
    }

    @Test
    void listUsersShouldApplyCustomScope() {
        TenantContext.setTenantId("tenant-a");
        UserAccount customScopedUser = loadScopedUser(TENANT_CUSTOM_SCOPE_USER, DataScopeType.CUSTOM, Set.of(3L));
        bind(customScopedUser);

        List<UserSummary> users = userDirectoryService.listUsers();

        assertThat(users).extracting(UserSummary::username)
                .contains(TENANT_OTHER_DEPT_USER)
                .doesNotContain(TENANT_TEST_USER, TENANT_CUSTOM_SCOPE_USER, PLATFORM_TEST_USER);
    }

    private void ensureUser(String tenantId, String username, Long deptId) {
        deleteUser(tenantId, username);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId(tenantId);
        entity.setDeptId(deptId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setPasswordHash(passwordHasher.hash("UserTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
    }

    private void deleteUser(String tenantId, String username) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", tenantId, username);
    }

    private Long ensureDept(String tenantId, String deptCode, String deptName, Long parentId) {
        deleteDept(tenantId, deptCode);
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId(tenantId);
        entity.setDeptCode(deptCode);
        entity.setDeptName(deptName);
        entity.setParentId(parentId);
        sysDeptMapper.insert(entity);
        return entity.getId();
    }

    private void deleteDept(String tenantId, String deptCode) {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", tenantId, deptCode);
    }

    private UserAccount loadScopedUser(String username, DataScopeType dataScopeType, Set<Long> customDeptIds) {
        SysUserEntity userEntity = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, "tenant-a")
                .eq(SysUserEntity::getUsername, username)
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
        return new UserAccount(
                userEntity.getId(),
                "tenant-a",
                username,
                userEntity.getPasswordHash(),
                true,
                Set.of(),
                Set.of("user:read"),
                customDeptIds,
                dataScopeType,
                userEntity.getSessionVersion() == null ? 1 : userEntity.getSessionVersion()
        );
    }
}
