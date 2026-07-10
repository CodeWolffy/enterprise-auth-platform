package com.enterprise.auth.platform.dept;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.modules.dept.application.DepartmentView;
import com.enterprise.auth.platform.modules.dept.application.DeptCatalogFacade;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.dept.interfaces.DeptCrudRequest;
import com.enterprise.auth.platform.modules.dept.application.DeptManagementService;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;

@SpringBootTest
class DeptManagementServiceTest {

    private static final String SCOPE_USER = "dept_scope_user_ut";
    private static final String HIDDEN_LEADER = "dept_hidden_leader_ut";
    private static final String CHILD_DEPT_CODE = "DEPT_SCOPE_CHILD_UT";
    private static final String PLATFORM_DEPT_CODE = "PLATFORM_DEPT_SCOPE_UT";

    @Autowired
    private DeptManagementService deptManagementService;

    @Autowired
    private DeptCatalogFacade deptCatalogFacade;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
        clear();
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?)", "tenant-a", SCOPE_USER, HIDDEN_LEADER);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "platform", PLATFORM_DEPT_CODE);
    }

    @Test
    void shouldCreateAndDeleteDeptInDatabaseMode() {
        TenantContext.setTenantId("platform");
        String deptCode = "TEST_DEPT_" + System.nanoTime();
        DepartmentView created = deptManagementService.create(
                new DeptCrudRequest(null, deptCode, "测试部门", null, "测试负责人", "13800138000", 12, 0)
        );

        assertThat(created.leaderName()).isEqualTo("测试负责人");
        assertThat(created.leaderPhone()).isEqualTo("13800138000");
        assertThat(created.orderNo()).isEqualTo(12);
        assertThat(created.enabled()).isZero();
        assertThat(deptCatalogFacade.departments())
                .filteredOn(item -> item.id().equals(created.id()))
                .first()
                .satisfies(item -> {
                    assertThat(item.leaderName()).isEqualTo("测试负责人");
                    assertThat(item.leaderPhone()).isEqualTo("13800138000");
                    assertThat(item.orderNo()).isEqualTo(12);
                    assertThat(item.enabled()).isZero();
                });

        deptManagementService.delete(created.id());

        assertThat(deptCatalogFacade.departments()).extracting(DepartmentView::id).doesNotContain(created.id());
    }

    @Test
    void shouldFilterDepartmentsByDataScope() {
        TenantContext.setTenantId("tenant-a");
        Long rootDeptId = tenantRootDeptId();
        Long childDeptId = ensureChildDept();
        Long userId = ensureScopedUser();

        UserAccount principal = new UserAccount(
                userId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("DeptTest@123"),
                true,
                Set.of(),
                Set.of("upms:sysdept:get"),
                Set.of(),
                DataScopeType.DEPT_AND_CHILDREN,
                1
        );
        bind(principal);

        assertThat(deptCatalogFacade.departments()).extracting(DepartmentView::id)
                .contains(rootDeptId, childDeptId);
    }

    @Test
    void shouldRejectManagingHiddenDepartment() {
        TenantContext.setTenantId("tenant-a");
        Long userId = ensureScopedUser();
        ensureHiddenLeader();
        UserAccount principal = new UserAccount(
                userId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("DeptTest@123"),
                true,
                Set.of(),
                Set.of("upms:sysdept:edit"),
                Set.of(),
                DataScopeType.DEPT,
                1
        );
        bind(principal);

        assertThatThrownBy(() -> deptManagementService.create(
                new DeptCrudRequest(tenantRootDeptId() + 999_999L, CHILD_DEPT_CODE, "隐藏子部门", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("父级部门");

        Long hiddenLeaderId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE tenant_id = ? AND username = ?",
                Long.class,
                "tenant-a",
                HIDDEN_LEADER
        );
        assertThatThrownBy(() -> deptManagementService.create(
                new DeptCrudRequest(tenantRootDeptId(), CHILD_DEPT_CODE, "负责人越权部门", hiddenLeaderId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("负责人");
    }

    @Test
    void platformAdminShouldSeeAllDepartmentsEvenWhenActiveTenantDiffers() {
        TenantContext.setTenantId("platform");
        deptManagementService.create(new DeptCrudRequest(null, PLATFORM_DEPT_CODE, "平台部门", null));

        TenantContext.setTenantId("tenant-a");
        Long userId = ensureScopedUser();
        UserAccount admin = new UserAccount(
                userId,
                "platform",
                "admin",
                passwordHasher.hash("DeptTest@123"),
                true,
                Set.of("ADMIN"),
                Set.of("upms:sysdept:get"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
        bind(admin);

        assertThat(deptCatalogFacade.departments()).extracting(DepartmentView::tenantId)
                .contains("platform", "tenant-a");
    }

    @Test
    void platformAdminShouldCreateDepartmentInRequestedTenant() {
        TenantContext.setGlobalScope("platform");
        UserAccount admin = new UserAccount(
                1L,
                "platform",
                "admin",
                passwordHasher.hash("DeptTest@123"),
                true,
                Set.of("ADMIN"),
                Set.of("upms:sysdept:add"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
        bind(admin);

        DepartmentView created = deptManagementService.create(
                new DeptCrudRequest(tenantRootDeptId(), CHILD_DEPT_CODE, "租户A部门", null, null, null, 0, 1, "tenant-a")
        );

        assertThat(created.tenantId()).isEqualTo("tenant-a");
        assertThat(created.parentId()).isEqualTo(tenantRootDeptId());
    }

    private Long ensureChildDept() {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptCode(CHILD_DEPT_CODE);
        entity.setDeptName("租户A-数据权限子部门");
        entity.setParentId(tenantRootDeptId());
        sysDeptMapper.insert(entity);
        return entity.getId();
    }

    private Long ensureScopedUser() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", SCOPE_USER);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptId(tenantRootDeptId());
        entity.setUsername(SCOPE_USER);
        entity.setDisplayName("部门权限测试用户");
        entity.setPasswordHash(passwordHasher.hash("DeptTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }

    private void ensureHiddenLeader() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", HIDDEN_LEADER);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptId(ensureHiddenDept());
        entity.setUsername(HIDDEN_LEADER);
        entity.setDisplayName("隐藏负责人");
        entity.setPasswordHash(passwordHasher.hash("DeptTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
    }

    private Long tenantRootDeptId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dept WHERE tenant_id = ? AND parent_id IS NULL AND deleted = 0 ORDER BY id LIMIT 1",
                Long.class,
                "tenant-a"
        );
    }

    private Long ensureHiddenDept() {
        String code = "DEPT_HIDDEN_UT";
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", code);
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptCode(code);
        entity.setDeptName("租户A-隐藏部门");
        entity.setParentId(tenantRootDeptId());
        sysDeptMapper.insert(entity);
        return entity.getId();
    }
}
