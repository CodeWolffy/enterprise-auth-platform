package com.enterprise.auth.platform.tenant;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.application.AuthPermissionSnapshotInvalidationService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantCatalogControllerTest {

    private static final String CAPABILITY_CODE = "tenant_capability_ut";
    private static final String PACKAGE_CODE = "tenant_package_ut";
    private static final String REFERENCED_TENANT_ID = "tenant-package-ut-ref";
    private static final String VISIBLE_RESOURCE_KEY = "tenant-package-ut-menu";
    private static final String GRANT_RESOURCE_KEY = "upms:tenant-package-ut:read";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @TestConfiguration
    static class TenantCatalogTestConfig {

        @Bean
        @Primary
        AuthPermissionSnapshotInvalidationService authPermissionSnapshotInvalidationService() {
            return new AuthPermissionSnapshotInvalidationService(null, null) {
                @Override
                public void invalidateAll() {
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        cleanupTestData();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    private void cleanupTestData() {
        jdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", REFERENCED_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_capability_resource_scope WHERE tenant_id = 'platform' AND capability_code = ?", CAPABILITY_CODE);
        jdbcTemplate.update("DELETE FROM sys_tenant_package_capability WHERE tenant_id = 'platform' AND package_code = ?", PACKAGE_CODE);
        jdbcTemplate.update("DELETE FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?", PACKAGE_CODE);
        jdbcTemplate.update("DELETE FROM sys_tenant_capability WHERE tenant_id = 'platform' AND capability_code = ?", CAPABILITY_CODE);
    }

    @Test
    void shouldManagePlatformTenantCatalog() throws Exception {
        UserAccount principal = principal(Set.of("upms:systenant:get", "upms:systenant:edit"));

        mockMvc.perform(post("/api/tenant-catalog/capabilities")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capabilityCode": "tenant_capability_ut",
                                  "capabilityName": "测试能力",
                                  "capabilityDesc": "用于租户目录接口测试",
                                  "sortOrder": 66,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilityCode").value(CAPABILITY_CODE));

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
                                  "orderNo": -10,
                                  "userQuota": 50,
                                  "storageQuotaGb": 20,
                                  "packageDesc": "用于租户目录接口测试",
                                  "enabled": true,
                                  "capabilityCodes": ["tenant_capability_ut"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCode").value(PACKAGE_CODE))
                .andExpect(jsonPath("$.data.subtitle").value("运营副标题"))
                .andExpect(jsonPath("$.data.salesPrice").value(19.90))
                .andExpect(jsonPath("$.data.originalPrice").value(29.90))
                .andExpect(jsonPath("$.data.descriptionMd").value("### 套餐详情"))
                .andExpect(jsonPath("$.data.appKey").value("tenant_app_ut"))
                .andExpect(jsonPath("$.data.orderNo").value(-10))
                .andExpect(jsonPath("$.data.capabilityCodes[0]").value(CAPABILITY_CODE));

        Long capabilityId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_tenant_capability WHERE tenant_id = 'platform' AND capability_code = ? LIMIT 1",
                Long.class,
                CAPABILITY_CODE
        );
        Long packageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ? LIMIT 1",
                Long.class,
                PACKAGE_CODE
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_capability_resource_scope(tenant_id, capability_code, resource_key, scope_type, required, created_by, updated_by) VALUES('platform', ?, ?, 'VISIBLE', 1, 'tester', 'tester')",
                CAPABILITY_CODE,
                VISIBLE_RESOURCE_KEY
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_capability_resource_scope(tenant_id, capability_code, resource_key, scope_type, required, created_by, updated_by) VALUES('platform', ?, ?, 'GRANT', 1, 'tester', 'tester')",
                CAPABILITY_CODE,
                GRANT_RESOURCE_KEY
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant(tenant_id, tenant_name, platform_level, tenant_status, package_code, lifecycle_note, created_by, updated_by, deleted) VALUES(?, '套餐引用测试租户', 0, 1, ?, '套餐引用影响分析', 'tester', 'tester', 0)",
                REFERENCED_TENANT_ID,
                PACKAGE_CODE
        );

        mockMvc.perform(get("/api/tenant-catalog/capabilities")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.capabilityCode=='" + CAPABILITY_CODE + "')]").exists());

        mockMvc.perform(get("/api/tenant-catalog/packages")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].packageCode").value(PACKAGE_CODE))
                .andExpect(jsonPath("$.data[0].visibleResourceCount").value(1))
                .andExpect(jsonPath("$.data[0].grantResourceCount").value(1))
                .andExpect(jsonPath("$.data[0].sampleResourceKeys", hasItems(VISIBLE_RESOURCE_KEY, GRANT_RESOURCE_KEY)))
                .andExpect(jsonPath("$.data[0].referencedTenantCount").value(1))
                .andExpect(jsonPath("$.data[0].referencedTenantIds[0]").value(REFERENCED_TENANT_ID));

        mockMvc.perform(get("/api/tenant-catalog/packages/{id}/impact", packageId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCode").value(PACKAGE_CODE))
                .andExpect(jsonPath("$.data.visibleResourceCount").value(1))
                .andExpect(jsonPath("$.data.grantResourceCount").value(1))
                .andExpect(jsonPath("$.data.sampleResourceKeys", hasItems(VISIBLE_RESOURCE_KEY, GRANT_RESOURCE_KEY)))
                .andExpect(jsonPath("$.data.referencedTenantCount").value(1))
                .andExpect(jsonPath("$.data.referencedTenantIds[0]").value(REFERENCED_TENANT_ID))
                .andExpect(jsonPath("$.data.rules[?(@.ruleCode=='PACKAGE_REFERENCED_TENANTS')].hit").value(true))
                .andExpect(jsonPath("$.data.recommendedActions[0]").value("先将引用租户迁移到新套餐，再执行删除或高风险变更。"));

        mockMvc.perform(put("/api/tenant-catalog/capabilities/{id}", capabilityId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capabilityCode": "tenant_capability_ut",
                                  "capabilityName": "测试能力已更新",
                                  "capabilityDesc": "用于租户目录接口测试更新",
                                  "sortOrder": 88,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.capabilityName").value("测试能力已更新"));

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
                                  "userQuota": 88,
                                  "storageQuotaGb": 30,
                                  "packageDesc": "用于租户目录接口测试更新",
                                  "enabled": true,
                                  "capabilityCodes": ["tenant_capability_ut"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageName").value("测试套餐已更新"))
                .andExpect(jsonPath("$.data.subtitle").value("运营副标题已更新"))
                .andExpect(jsonPath("$.data.salesPrice").value(39.90))
                .andExpect(jsonPath("$.data.originalPrice").value(59.90))
                .andExpect(jsonPath("$.data.descriptionMd").value("### 套餐详情已更新"))
                .andExpect(jsonPath("$.data.appKey").value("tenant_app_ut_updated"))
                .andExpect(jsonPath("$.data.orderNo").value(30));

        mockMvc.perform(delete("/api/tenant-catalog/packages/{id}", packageId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("该套餐仍被租户使用，暂不允许删除"));

        jdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", REFERENCED_TENANT_ID);

        mockMvc.perform(delete("/api/tenant-catalog/packages/{id}", packageId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(delete("/api/tenant-catalog/capabilities/{id}", capabilityId)
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private UserAccount principal(Set<String> permissions) {
        return new UserAccount(
                1L,
                "platform",
                "admin",
                passwordHasher.hash("Admin@123456"),
                true,
                Set.of("ADMIN"),
                permissions,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
