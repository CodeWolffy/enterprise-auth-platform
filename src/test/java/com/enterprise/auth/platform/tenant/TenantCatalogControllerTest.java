package com.enterprise.auth.platform.tenant;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class TenantCatalogControllerTest {

    private static final String PACKAGE_CODE = "tenant_package_ut";
    private static final String REFERENCED_TENANT_ID = "tenant-package-ut-ref";
    private static final String APP_KEY = "tenant_app_ut";
    private static final String UPDATED_APP_KEY = "tenant_app_ut_updated";

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
        cleanupTestData();
        when(userAuthenticationFacade.findById(1L)).thenReturn(Optional.of(authenticationUser()));
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    void shouldManageTenantPackagesAndSyncReferencedTenantMenus() throws Exception {
        UserAccount principal = principal();

        mockMvc.perform(post("/api/tenant-catalog/packages")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packageCode": "tenant_package_ut",
                                  "packageName": "测试套餐",
                                  "subtitle": "运营副标题",
                                  "salesPrice": 19.90,
                                  "originalPrice": 29.90,
                                  "descriptionMd": "### 套餐详情",
                                  "appKey": "tenant_app_ut",
                                  "orderNo": 10,
                                  "packageDesc": "用于租户目录接口测试",
                                  "status": "0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCode").value(PACKAGE_CODE))
                .andExpect(jsonPath("$.data.appKey").value(APP_KEY))
                .andExpect(jsonPath("$.data.status").value("0"));

        Long packageId = packageId();
        Long syncedMenuId = seedMenu(UPDATED_APP_KEY);
        jdbcTemplate.update(
                "INSERT INTO sys_tenant(tenant_id, tenant_name, platform_level, tenant_status, package_code, lifecycle_note, created_by, updated_by, deleted) VALUES(?, '套餐引用测试租户', 0, 1, ?, '套餐引用影响分析', 'tester', 'tester', 0)",
                REFERENCED_TENANT_ID,
                PACKAGE_CODE
        );

        mockMvc.perform(get("/api/tenant-catalog/packages/{id}/impact", packageId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCode").value(PACKAGE_CODE))
                .andExpect(jsonPath("$.data.appKey").value(APP_KEY))
                .andExpect(jsonPath("$.data.referencedTenantCount").value(1))
                .andExpect(jsonPath("$.data.referencedTenantIds[0]").value(REFERENCED_TENANT_ID))
                .andExpect(jsonPath("$.data.rules[?(@.ruleCode=='PACKAGE_REFERENCED_TENANTS')].hit").value(true));

        mockMvc.perform(put("/api/tenant-catalog/packages/{id}", packageId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "packageCode": "tenant_package_ut",
                                  "packageName": "测试套餐已更新",
                                  "subtitle": "运营副标题已更新",
                                  "salesPrice": 39.90,
                                  "originalPrice": 59.90,
                                  "descriptionMd": "### 套餐详情已更新",
                                  "appKey": "tenant_app_ut_updated",
                                  "orderNo": 30,
                                  "packageDesc": "用于租户目录接口测试更新",
                                  "status": "0"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageName").value("测试套餐已更新"))
                .andExpect(jsonPath("$.data.appKey").value(UPDATED_APP_KEY))
                .andExpect(jsonPath("$.data.referencedTenantCount").value(1));

        Integer syncedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_tenant_menu WHERE tenant_id = ? AND menu_id = ?",
                Integer.class,
                REFERENCED_TENANT_ID,
                syncedMenuId
        );
        org.junit.jupiter.api.Assertions.assertEquals(1, syncedCount);

        mockMvc.perform(delete("/api/tenant-catalog/packages/{id}", packageId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("该套餐仍被租户使用，暂不允许删除"));

        jdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", REFERENCED_TENANT_ID);

        mockMvc.perform(delete("/api/tenant-catalog/packages/{id}", packageId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private Long packageId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ? LIMIT 1",
                Long.class,
                PACKAGE_CODE
        );
    }

    private Long seedMenu(String appKey) {
        jdbcTemplate.update(
                "INSERT INTO sys_menu(parent_id, name, permission, path, component, sort, type, application_key, del_flag) VALUES(NULL, ?, NULL, ?, ?, 1, '0', ?, '0')",
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

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM sys_tenant_menu WHERE tenant_id = ?", REFERENCED_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", REFERENCED_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?", PACKAGE_CODE);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE application_key IN (?, ?)", APP_KEY, UPDATED_APP_KEY);
    }

    private UserAccount principal() {
        return new UserAccount(
                1L,
                "platform",
                "admin",
                "{noop}ignored",
                true,
                Set.of("ADMIN"),
                Set.of("upms:tenantpackage:page", "upms:tenantpackage:get", "upms:tenantpackage:add",
                        "upms:tenantpackage:edit", "upms:tenantpackage:del"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }

    private AuthenticationUser authenticationUser() {
        return new AuthenticationUser(
                1L,
                "platform",
                "admin",
                "{noop}ignored",
                true,
                Set.of("ADMIN"),
                Set.of("upms:tenantpackage:page", "upms:tenantpackage:get", "upms:tenantpackage:add",
                        "upms:tenantpackage:edit", "upms:tenantpackage:del"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
