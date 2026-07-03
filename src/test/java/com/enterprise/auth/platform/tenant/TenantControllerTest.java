package com.enterprise.auth.platform.tenant;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.user.application.AuthenticationUser;
import com.enterprise.auth.platform.modules.user.application.UserAuthenticationFacade;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantControllerTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String HISTORY_TENANT_ID = "platform";
    private static final String STATUS_SUMMARY = "历史筛选-状态";
    private static final String PACKAGE_SUMMARY = "历史筛选-套餐";
    private static final String LINKAGE_TENANT_ID = "tenant-package-linkage-ut";
    private static final String LINKAGE_PACKAGE_CODE = "tenant-package-linkage-ut";
    private static final String LINKAGE_PACKAGE_NAME = "租户联动测试套餐";
    private static final String APP_KEY = "tenant-menu-ut";
    private static final String OTHER_APP_KEY = "tenant-menu-ut-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;

    @MockitoBean
    private UserAuthenticationFacade userAuthenticationFacade;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)",
                HISTORY_TENANT_ID, STATUS_SUMMARY, PACKAGE_SUMMARY
        );
        cleanupLinkageData();
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,DATE_SUB(NOW(), INTERVAL 2 DAY))",
                HISTORY_TENANT_ID, "STATUS", "tenantStatus", "0", "1", STATUS_SUMMARY, "tester"
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,NOW())",
                HISTORY_TENANT_ID, "PACKAGE", "packageCode", "basic", "pro", PACKAGE_SUMMARY, "another"
        );
        when(userAuthenticationFacade.findById(1L)).thenReturn(Optional.of(authenticationUser()));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)",
                HISTORY_TENANT_ID, STATUS_SUMMARY, PACKAGE_SUMMARY
        );
        cleanupLinkageData();
    }

    @Test
    void currentTenantResolvesFromHeader() throws Exception {
        mockMvc.perform(get("/api/tenants/current")
                        .with(bearer(principal("upms:systenant:get"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID));
    }

    @Test
    void tenantHistoryShouldSupportFilters() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}/history", HISTORY_TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), HISTORY_TENANT_ID))
                        .header("X-Tenant-Id", HISTORY_TENANT_ID)
                        .param("changeType", "STATUS")
                        .param("operator", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].changeType").value("STATUS"))
                .andExpect(jsonPath("$.data.records[0].summary").value(STATUS_SUMMARY))
                .andExpect(jsonPath("$.data.records[0].impactSummary").isNotEmpty());
    }

    @Test
    void tenantHistorySummaryShouldReturnTrajectoryOverview() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}/history/summary", HISTORY_TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), HISTORY_TENANT_ID))
                        .header("X-Tenant-Id", HISTORY_TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(HISTORY_TENANT_ID))
                .andExpect(jsonPath("$.data.totalChanges").isNumber())
                .andExpect(jsonPath("$.data.packageChanges").isNumber())
                .andExpect(jsonPath("$.data.menuChanges").isNumber())
                .andExpect(jsonPath("$.data.recentTimeline").isArray());
    }

    @Test
    void tenantHistoryShouldRejectTimezoneLessRangeParameters() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}/history", HISTORY_TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), HISTORY_TENANT_ID))
                        .header("X-Tenant-Id", HISTORY_TENANT_ID)
                        .param("from", "2026-03-01T00:00:00")
                        .param("to", "2026-03-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantProfileFieldsShouldBePersistedAndReturned() throws Exception {
        seedLinkagePackage(APP_KEY);
        java.time.Instant authBeginInstant = java.time.Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String authBeginAt = authBeginInstant.toString();
        String expireAt = authBeginInstant.plus(java.time.Duration.ofDays(30)).toString();

        mockMvc.perform(post("/api/tenants")
                        .with(bearer(principal("upms:systenant:add"), "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-package-linkage-ut",
                                  "tenantName": "租户资料字段测试",
                                  "platformLevel": false,
                                  "tenantStatus": 1,
                                  "authBeginAt": "%s",
                                  "expireAt": "%s",
                                  "packageCode": "tenant-package-linkage-ut",
                                  "logoUrl": "https://cdn.example.com/logo.png",
                                  "contactName": "张三",
                                  "contactPhone": "13800000000",
                                  "contactEmail": "tenant@example.com",
                                  "website": "https://tenant.example.com",
                                  "address": "上海市浦东新区",
                                  "lifecycleNote": "资料字段创建"
                                }
                                """.formatted(authBeginAt, expireAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageName").value(LINKAGE_PACKAGE_NAME))
                .andExpect(jsonPath("$.data.authBeginAt").value(authBeginAt))
                .andExpect(jsonPath("$.data.expireAt").value(expireAt))
                .andExpect(jsonPath("$.data.logoUrl").value("https://cdn.example.com/logo.png"))
                .andExpect(jsonPath("$.data.contactName").value("张三"))
                .andExpect(jsonPath("$.data.contactPhone").value("13800000000"))
                .andExpect(jsonPath("$.data.contactEmail").value("tenant@example.com"))
                .andExpect(jsonPath("$.data.website").value("https://tenant.example.com"))
                .andExpect(jsonPath("$.data.address").value("上海市浦东新区"));

        mockMvc.perform(get("/api/tenants/{tenantId}", LINKAGE_TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), "platform"))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(LINKAGE_TENANT_ID))
                .andExpect(jsonPath("$.data.name").value("租户资料字段测试"))
                .andExpect(jsonPath("$.data.packageName").value(LINKAGE_PACKAGE_NAME));

        mockMvc.perform(put("/api/tenants/{tenantId}", LINKAGE_TENANT_ID)
                        .with(bearer(principal("upms:systenant:edit"), "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-package-linkage-ut",
                                  "tenantName": "租户资料字段测试更新",
                                  "platformLevel": false,
                                  "tenantStatus": 1,
                                  "authBeginAt": "%s",
                                  "expireAt": "%s",
                                  "packageCode": "tenant-package-linkage-ut",
                                  "logoUrl": "https://cdn.example.com/logo-updated.png",
                                  "contactName": "李四",
                                  "contactPhone": "13900000000",
                                  "contactEmail": "tenant-updated@example.com",
                                  "website": "https://tenant-updated.example.com",
                                  "address": "北京市朝阳区",
                                  "lifecycleNote": "资料字段更新"
                                }
                                """.formatted(authBeginAt, expireAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("租户资料字段测试更新"))
                .andExpect(jsonPath("$.data.logoUrl").value("https://cdn.example.com/logo-updated.png"))
                .andExpect(jsonPath("$.data.contactName").value("李四"))
                .andExpect(jsonPath("$.data.contactPhone").value("13900000000"))
                .andExpect(jsonPath("$.data.contactEmail").value("tenant-updated@example.com"))
                .andExpect(jsonPath("$.data.website").value("https://tenant-updated.example.com"))
                .andExpect(jsonPath("$.data.address").value("北京市朝阳区"));

        assertTenantBootstrapCreated();
    }

    @Test
    void tenantMenusShouldBeAssignedAndCleanRoleMenusOutsideTenantScope() throws Exception {
        Long packageMenuId = seedMenu(APP_KEY);
        Long otherMenuId = seedMenu(OTHER_APP_KEY);
        seedLinkagePackage(APP_KEY);

        mockMvc.perform(post("/api/tenants")
                        .with(bearer(principal("upms:systenant:add"), "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-package-linkage-ut",
                                  "tenantName": "租户菜单联动测试",
                                  "platformLevel": false,
                                  "tenantStatus": 1,
                                  "packageCode": "tenant-package-linkage-ut",
                                  "lifecycleNote": "创建时继承套餐菜单"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageName").value(LINKAGE_PACKAGE_NAME));

        mockMvc.perform(get("/api/tenants/{tenantId}/menus", LINKAGE_TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), "platform"))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasItem(packageMenuId.intValue())));

        Long adminRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = 'TENANT_ADMIN' AND deleted = 0 LIMIT 1",
                Long.class,
                LINKAGE_TENANT_ID
        );
        Integer oldRoleMenuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE tenant_id = ? AND role_id = ? AND menu_id = ?",
                Integer.class,
                LINKAGE_TENANT_ID,
                adminRoleId,
                packageMenuId
        );
        Assertions.assertEquals(1, oldRoleMenuCount);

        mockMvc.perform(put("/api/tenants/{tenantId}/menus", LINKAGE_TENANT_ID)
                        .with(bearer(principal("upms:systenant:edit"), "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "menuIds": [%d]
                                }
                                """.formatted(otherMenuId)))
                .andExpect(status().isOk());

        Integer newTenantMenuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_tenant_menu WHERE tenant_id = ? AND menu_id = ?",
                Integer.class,
                LINKAGE_TENANT_ID,
                otherMenuId
        );
        Integer cleanedRoleMenuCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE tenant_id = ? AND role_id = ? AND menu_id = ?",
                Integer.class,
                LINKAGE_TENANT_ID,
                adminRoleId,
                packageMenuId
        );
        Assertions.assertEquals(1, newTenantMenuCount);
        Assertions.assertEquals(0, cleanedRoleMenuCount);
    }

    private void assertTenantBootstrapCreated() {
        Integer rootDeptCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_dept WHERE tenant_id = ? AND dept_code = 'ROOT' AND deleted = 0",
                Integer.class,
                LINKAGE_TENANT_ID
        );
        Integer adminRoleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_role WHERE tenant_id = ? AND role_code = 'TENANT_ADMIN' AND deleted = 0",
                Integer.class,
                LINKAGE_TENANT_ID
        );
        Integer adminUserCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE tenant_id = ? AND username = ? AND enabled = 1 AND must_change_password = 1 AND deleted = 0",
                Integer.class,
                LINKAGE_TENANT_ID,
                LINKAGE_TENANT_ID + "_admin"
        );
        Integer adminUserRoleCount = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM sys_user_role ur
                        JOIN sys_user u ON u.id = ur.user_id AND u.tenant_id = ur.tenant_id
                        JOIN sys_role r ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id
                        WHERE ur.tenant_id = ?
                          AND u.username = ?
                          AND r.role_code = 'TENANT_ADMIN'
                        """,
                Integer.class,
                LINKAGE_TENANT_ID,
                LINKAGE_TENANT_ID + "_admin"
        );

        Assertions.assertEquals(1, rootDeptCount);
        Assertions.assertEquals(1, adminRoleCount);
        Assertions.assertEquals(1, adminUserCount);
        Assertions.assertEquals(1, adminUserRoleCount);
    }

    private void seedLinkagePackage(String appKey) {
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_package(tenant_id, package_code, package_name, app_key, package_desc, status, created_by, updated_by, deleted) VALUES('platform', ?, ?, ?, '租户套餐联动测试', '0', 'tester', 'tester', 0)",
                LINKAGE_PACKAGE_CODE,
                LINKAGE_PACKAGE_NAME,
                appKey
        );
    }

    private Long seedMenu(String appKey) {
        jdbcTemplate.update(
                "INSERT INTO sys_menu(parent_id, name, permission, path, component, sort, type, application_key, deleted) VALUES(NULL, ?, NULL, ?, ?, 1, '0', ?, 0)",
                "测试菜单-" + appKey,
                "/test/" + appKey,
                "TestView",
                appKey
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE application_key = ? ORDER BY id DESC LIMIT 1",
                Long.class,
                appKey
        );
    }

    private void cleanupLinkageData() {
        jdbcTemplate.update("DELETE FROM sys_tenant_change_log WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_security_policy WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_menu WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?", LINKAGE_PACKAGE_CODE);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE application_key IN (?, ?)", APP_KEY, OTHER_APP_KEY);
    }

    private UserAccount principal(String authority) {
        java.util.LinkedHashSet<String> authorities = new java.util.LinkedHashSet<>();
        authorities.add("upms:systenant:page");
        authorities.add("upms:systenant:get");
        authorities.add("upms:systenant:add");
        authorities.add("upms:systenant:edit");
        authorities.add("upms:systenant:del");
        authorities.add(authority);
        return new UserAccount(
                1L,
                "platform",
                "tester",
                "{noop}ignored",
                true,
                Set.of("TENANT_ADMIN"),
                authorities,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }

    private AuthenticationUser authenticationUser() {
        java.util.LinkedHashSet<String> authorities = new java.util.LinkedHashSet<>();
        authorities.add("upms:systenant:page");
        authorities.add("upms:systenant:get");
        authorities.add("upms:systenant:add");
        authorities.add("upms:systenant:edit");
        authorities.add("upms:systenant:del");
        return new AuthenticationUser(
                1L,
                "platform",
                "tester",
                "{noop}ignored",
                true,
                Set.of("TENANT_ADMIN"),
                authorities,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
