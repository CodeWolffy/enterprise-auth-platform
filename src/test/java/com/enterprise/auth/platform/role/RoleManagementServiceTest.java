package com.enterprise.auth.platform.role;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bind;
import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.clear;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.resource.application.CatalogService;
import com.enterprise.auth.platform.modules.role.application.RoleManagementService;
import com.enterprise.auth.platform.modules.role.interfaces.CreateRoleRequest;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class RoleManagementServiceTest {

    private static final String ROLE_CODE = "role_mgmt_ut";
    private static final String PLATFORM_ROLE_CODE = "platform_role_mgmt_ut";
    private static final String TENANT = "tenant-a";

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private CatalogService catalogService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        for (String tenant : new String[]{TENANT, "platform"}) {
            jdbcTemplate.update(
                    "DELETE FROM sys_role_menu WHERE tenant_id = ? AND role_id IN (SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?)",
                    tenant, tenant, ROLE_CODE);
            jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", tenant, ROLE_CODE);
            jdbcTemplate.update(
                    "DELETE FROM sys_role_menu WHERE tenant_id = ? AND role_id IN (SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?)",
                    tenant, tenant, PLATFORM_ROLE_CODE);
            jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", tenant, PLATFORM_ROLE_CODE);
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
        TenantContext.clear();
        clear();
    }

    private void setupContext() {
        TenantContext.setTenantId(TENANT);
        bind(tenantAdmin(TENANT, Set.of("upms:sysrole:edit")));
    }

    @Test
    void createRoleShouldSucceed() {
        setupContext();

        CreateRoleRequest request = new CreateRoleRequest(ROLE_CODE, "UT 角色", "测试", DataScopeType.ALL, null);
        var result = roleManagementService.create(request);

        assertThat(result).isNotNull();
        assertThat(result.code()).isEqualTo(ROLE_CODE);
        assertThat(result.name()).isEqualTo("UT 角色");

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                Long.class, TENANT, ROLE_CODE
        );
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void createDuplicateRoleCodeShouldFail() {
        setupContext();

        CreateRoleRequest request = new CreateRoleRequest(ROLE_CODE, "UT 角色", "测试", DataScopeType.ALL, null);
        roleManagementService.create(request);

        setupContext(); // re-set tenant context before second call
        assertThatThrownBy(() -> roleManagementService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色编码已存在");
    }

    @Test
    void updateRoleShouldChangeNameAndDesc() {
        setupContext();

        CreateRoleRequest createReq = new CreateRoleRequest(ROLE_CODE, "原名", "原描述", DataScopeType.ALL, null);
        roleManagementService.create(createReq);

        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                Long.class, TENANT, ROLE_CODE
        );

        setupContext(); // re-set before update
        CreateRoleRequest updateReq = new CreateRoleRequest(ROLE_CODE, "新名", "新描述", DataScopeType.ALL, null);
        var updated = roleManagementService.update(roleId, updateReq);

        assertThat(updated.name()).isEqualTo("新名");
    }

    @Test
    void impactShouldBlockDeleteWhenRoleAssignedToUsers() {
        setupContext();

        CreateRoleRequest request = new CreateRoleRequest(ROLE_CODE, "UT 角色", "测试", DataScopeType.ALL, null);
        roleManagementService.create(request);
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                Long.class, TENANT, ROLE_CODE
        );
        jdbcTemplate.update(
                "INSERT INTO sys_user_role(tenant_id, user_id, role_id, created_by, updated_by) VALUES(?, ?, ?, 'tester', 'tester')",
                TENANT, 99101L, roleId
        );

        var impact = roleManagementService.impact(roleId);

        assertThat(impact.assignedUserCount()).isEqualTo(1);
        assertThat(impact.sampleUserIds()).containsExactly(99101L);
        assertThat(impact.deleteBlocked()).isTrue();
        assertThat(impact.warnings()).anyMatch(item -> item.contains("需先调整用户角色"));

        setupContext();
        assertThatThrownBy(() -> roleManagementService.delete(roleId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色已分配给用户");
    }

    @Test
    void platformAdminShouldSeeAllRolesEvenWhenActiveTenantDiffers() {
        TenantContext.setTenantId(TENANT);
        bind(tenantAdmin(TENANT, Set.of("upms:sysrole:edit")));
        roleManagementService.create(new CreateRoleRequest(ROLE_CODE, "租户角色", "租户", DataScopeType.ALL, null));

        TenantContext.setTenantId("platform");
        bind(tenantAdmin("platform", Set.of("upms:sysrole:edit")));
        roleManagementService.create(new CreateRoleRequest(PLATFORM_ROLE_CODE, "平台角色", "平台", DataScopeType.ALL, null));

        TenantContext.setTenantId(TENANT);
        bind(tenantAdmin("platform", Set.of("upms:sysrole:get")));

        assertThat(catalogService.roles()).extracting(CatalogService.RoleView::tenantId)
                .contains("platform", TENANT);
    }

    @Test
    void platformAdminShouldCreateRoleInRequestedTenant() {
        TenantContext.setGlobalScope("platform");
        bind(tenantAdmin("platform", Set.of("upms:sysrole:add")));

        var result = roleManagementService.create(new CreateRoleRequest(
                ROLE_CODE,
                "租户A角色",
                "平台管理员创建",
                DataScopeType.CUSTOM,
                java.util.List.of(2L),
                TENANT
        ));

        assertThat(result.tenantId()).isEqualTo(TENANT);
        assertThat(result.customDeptIds()).containsExactly(2L);

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                Long.class,
                TENANT,
                ROLE_CODE
        );
        assertThat(count).isEqualTo(1L);
    }

    private static UserAccount tenantAdmin(String tenantId, Set<String> permissions) {
        return new UserAccount(
                1L, tenantId, "test_admin", "{noop}ignored",
                true, Set.of("ADMIN"), permissions, Set.of(),
                DataScopeType.ALL, 1
        );
    }
}
