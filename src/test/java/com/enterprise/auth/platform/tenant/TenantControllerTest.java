package com.enterprise.auth.platform.tenant;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantControllerTest {

    private static final String TENANT_ID = "tenant-a";
    private static final String STATUS_SUMMARY = "历史筛选-状态";
    private static final String PACKAGE_SUMMARY = "历史筛选-套餐";
    private static final String LINKAGE_TENANT_ID = "tenant-package-linkage-ut";
    private static final String LINKAGE_PACKAGE_CODE = "tenant-package-linkage-ut";
    private static final String LINKAGE_PACKAGE_NAME = "租户联动测试套餐";
    private static final String ORIGINAL_CAPABILITY = "auth";
    private static final String EXTRA_CAPABILITY = "audit";
    private static final long SEVEN_DAYS_MS = 7L * 24 * 3600 * 1000;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AuthPermissionSnapshotInvalidationService permissionSnapshotInvalidationService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)",
                TENANT_ID, STATUS_SUMMARY, PACKAGE_SUMMARY
        );
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_capability_override WHERE tenant_id = ? AND capability_code IN (?, ?)",
                TENANT_ID, "audit", "notice"
        );
        cleanupLinkageData();
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,DATE_SUB(NOW(), INTERVAL 2 DAY))",
                TENANT_ID, "STATUS", "tenantStatus", "0", "1", STATUS_SUMMARY, "tester"
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_change_log(tenant_id, change_type, field_key, old_value, new_value, summary, operator, occurred_at) VALUES(?,?,?,?,?,?,?,NOW())",
                TENANT_ID, "PACKAGE", "packageCode", "basic", "pro", PACKAGE_SUMMARY, "another"
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_change_log WHERE tenant_id = ? AND summary IN (?, ?)",
                TENANT_ID, STATUS_SUMMARY, PACKAGE_SUMMARY
        );
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_capability_override WHERE tenant_id = ? AND capability_code IN (?, ?)",
                TENANT_ID, "audit", "notice"
        );
        cleanupLinkageData();
    }

    private void cleanupLinkageData() {
        jdbcTemplate.update("DELETE FROM sys_tenant_change_log WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_capability_override WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_security_policy WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant WHERE tenant_id = ?", LINKAGE_TENANT_ID);
        jdbcTemplate.update("DELETE FROM sys_tenant_package_capability WHERE tenant_id = 'platform' AND package_code = ?", LINKAGE_PACKAGE_CODE);
        jdbcTemplate.update("DELETE FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?", LINKAGE_PACKAGE_CODE);
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
        long now = System.currentTimeMillis();
        mockMvc.perform(get("/api/tenants/{tenantId}/history", TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("changeType", "STATUS")
                        .param("operator", "test")
                        .param("fromEpochMs", String.valueOf(now - SEVEN_DAYS_MS))
                        .param("toEpochMs", String.valueOf(now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].changeType").value("STATUS"))
                .andExpect(jsonPath("$.data.records[0].summary").value(STATUS_SUMMARY))
                .andExpect(jsonPath("$.data.records[0].impactSummary").isNotEmpty());
    }

    @Test
    void tenantHistorySummaryShouldReturnTrajectoryOverview() throws Exception {
        long now = System.currentTimeMillis();
        mockMvc.perform(get("/api/tenants/{tenantId}/history/summary", TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("fromEpochMs", String.valueOf(now - SEVEN_DAYS_MS))
                        .param("toEpochMs", String.valueOf(now)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.totalChanges").isNumber())
                .andExpect(jsonPath("$.data.packageChanges").isNumber())
                .andExpect(jsonPath("$.data.recentTimeline").isArray());
    }

    @Test
    void tenantHistoryShouldRejectLocalDateTimeWithoutTimezone() throws Exception {
        mockMvc.perform(get("/api/tenants/{tenantId}/history", TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .param("fromEpochMs", "2026-03-01T00:00:00")
                        .param("toEpochMs", "2026-03-31T23:59:59"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tenantProfileFieldsShouldBePersistedAndReturned() throws Exception {
        seedLinkagePackage();
        long authBeginAt = (System.currentTimeMillis() / 1000) * 1000;
        long expireAt = authBeginAt + 30L * 24 * 3600 * 1000;

        UserAccount principal = principal("upms:systenant:edit");
        mockMvc.perform(post("/api/tenants")
                        .with(bearer(principal, "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-package-linkage-ut",
                                  "tenantName": "租户资料字段测试",
                                  "platformLevel": false,
                                  "tenantStatus": 1,
                                  "authBeginAt": %d,
                                  "expireAt": %d,
                                  "packageCode": "tenant-package-linkage-ut",
                                  "capabilityCodes": null,
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
                .andExpect(jsonPath("$.data.authBeginAt").value(authBeginAt))
                .andExpect(jsonPath("$.data.expireAt").value(expireAt))
                .andExpect(jsonPath("$.data.logoUrl").value("https://cdn.example.com/logo.png"))
                .andExpect(jsonPath("$.data.contactName").value("张三"))
                .andExpect(jsonPath("$.data.contactPhone").value("13800000000"))
                .andExpect(jsonPath("$.data.contactEmail").value("tenant@example.com"))
                .andExpect(jsonPath("$.data.website").value("https://tenant.example.com"))
                .andExpect(jsonPath("$.data.address").value("上海市浦东新区"));

        mockMvc.perform(put("/api/tenants/{tenantId}", LINKAGE_TENANT_ID)
                        .with(bearer(principal, "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-package-linkage-ut",
                                  "tenantName": "租户资料字段测试更新",
                                  "platformLevel": false,
                                  "tenantStatus": 1,
                                  "authBeginAt": %d,
                                  "expireAt": %d,
                                  "packageCode": "tenant-package-linkage-ut",
                                  "capabilityCodes": ["auth"],
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

        mockMvc.perform(get("/api/tenants")
                        .with(bearer(principal("upms:systenant:get"), "platform"))
                        .header("X-Tenant-Id", "platform")
                        .param("keyword", "tenant-package-linkage-ut"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].contactName").value("李四"))
                .andExpect(jsonPath("$.data.records[0].authBeginAt").value(authBeginAt))
                .andExpect(jsonPath("$.data.records[0].expireAt").value(expireAt));

        assertTenantBootstrapCreated();
    }

    @Test
    void tenantPackageLinkageShouldExposeSummaryAndKeepPackageDefinitionImmutable() throws Exception {
        seedLinkagePackage();

        UserAccount principal = principal("upms:systenant:edit");
        mockMvc.perform(post("/api/tenants")
                        .with(bearer(principal, "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-package-linkage-ut",
                                  "tenantName": "租户套餐联动测试",
                                  "platformLevel": false,
                                  "tenantStatus": 1,
                                  "packageCode": "tenant-package-linkage-ut",
                                  "packageName": "不应反写套餐名称",
                                  "userQuota": 999,
                                  "storageQuotaGb": 999,
                                  "capabilityCodes": null,
                                  "lifecycleNote": "创建时继承套餐"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageName").value(LINKAGE_PACKAGE_NAME))
                .andExpect(jsonPath("$.data.userQuota").value(10))
                .andExpect(jsonPath("$.data.storageQuotaGb").value(5))
                .andExpect(jsonPath("$.data.capabilityCodes[0]").value(ORIGINAL_CAPABILITY));

        mockMvc.perform(get("/api/platform/tenants/{tenantId}/capability-summary", LINKAGE_TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), "platform"))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCapabilityCount").value(1))
                .andExpect(jsonPath("$.data.effectiveCapabilityCount").value(1))
                .andExpect(jsonPath("$.data.packageCapabilityCodes[0]").value(ORIGINAL_CAPABILITY));

        mockMvc.perform(put("/api/tenants/{tenantId}", LINKAGE_TENANT_ID)
                        .with(bearer(principal, "platform"))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tenantId": "tenant-package-linkage-ut",
                                  "tenantName": "租户套餐联动测试更新",
                                  "platformLevel": false,
                                  "tenantStatus": 1,
                                  "packageCode": "tenant-package-linkage-ut",
                                  "packageName": "不应反写套餐名称-更新",
                                  "userQuota": 888,
                                  "storageQuotaGb": 888,
                                  "capabilityCodes": ["auth", "audit"],
                                  "lifecycleNote": "仅保存租户自身覆盖"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageName").value(LINKAGE_PACKAGE_NAME))
                .andExpect(jsonPath("$.data.userQuota").value(10))
                .andExpect(jsonPath("$.data.storageQuotaGb").value(5))
                .andExpect(jsonPath("$.data.capabilityCodes[0]").value(ORIGINAL_CAPABILITY))
                .andExpect(jsonPath("$.data.capabilityCodes[1]").value(EXTRA_CAPABILITY));

        mockMvc.perform(get("/api/tenants/{tenantId}/capability-summary", LINKAGE_TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), "platform"))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCapabilityCount").value(1))
                .andExpect(jsonPath("$.data.effectiveCapabilityCount").value(2))
                .andExpect(jsonPath("$.data.addedCapabilityCount").value(1))
                .andExpect(jsonPath("$.data.addedCapabilities[0]").value(EXTRA_CAPABILITY));

        Integer packageCapabilityCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_tenant_package_capability WHERE tenant_id = 'platform' AND package_code = ?",
                Integer.class,
                LINKAGE_PACKAGE_CODE
        );
        String packageName = jdbcTemplate.queryForObject(
                "SELECT package_name FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?",
                String.class,
                LINKAGE_PACKAGE_CODE
        );
        Integer userQuota = jdbcTemplate.queryForObject(
                "SELECT user_quota FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?",
                Integer.class,
                LINKAGE_PACKAGE_CODE
        );
        Integer storageQuota = jdbcTemplate.queryForObject(
                "SELECT storage_quota_gb FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?",
                Integer.class,
                LINKAGE_PACKAGE_CODE
        );

        org.junit.jupiter.api.Assertions.assertEquals(1, packageCapabilityCount);
        org.junit.jupiter.api.Assertions.assertEquals(LINKAGE_PACKAGE_NAME, packageName);
        org.junit.jupiter.api.Assertions.assertEquals(10, userQuota);
        org.junit.jupiter.api.Assertions.assertEquals(5, storageQuota);
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

        org.junit.jupiter.api.Assertions.assertEquals(1, rootDeptCount);
        org.junit.jupiter.api.Assertions.assertEquals(1, adminRoleCount);
        org.junit.jupiter.api.Assertions.assertEquals(1, adminUserCount);
        org.junit.jupiter.api.Assertions.assertEquals(1, adminUserRoleCount);
    }

    private void seedLinkagePackage() {
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_package(tenant_id, package_code, package_name, user_quota, storage_quota_gb, package_desc, enabled, created_by, updated_by, deleted) VALUES('platform', ?, ?, 10, 5, '租户套餐联动测试', 1, 'tester', 'tester', 0)",
                LINKAGE_PACKAGE_CODE,
                LINKAGE_PACKAGE_NAME
        );
        jdbcTemplate.update(
                "INSERT INTO sys_tenant_package_capability(tenant_id, package_code, capability_code, created_by, updated_by) VALUES('platform', ?, ?, 'tester', 'tester')",
                LINKAGE_PACKAGE_CODE,
                ORIGINAL_CAPABILITY
        );
    }

    @Test
    void tenantCapabilityOverridesShouldBeQueriedAndUpdated() throws Exception {
        mockMvc.perform(put("/api/tenants/{tenantId}/capability-overrides", TENANT_ID)
                        .with(bearer(principal("upms:systenant:edit"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "capabilityCode": "audit",
                                      "enabled": true,
                                      "capabilityDescOverride": "审计导出与看板能力"
                                    },
                                    {
                                      "capabilityCode": "notice",
                                      "enabled": false
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.overrides[?(@.capabilityCode=='audit')].overrideEnabled").value(true));

        mockMvc.perform(get("/api/tenants/{tenantId}/capability-overrides", TENANT_ID)
                        .with(bearer(principal("upms:systenant:get"), TENANT_ID))
                        .header("X-Tenant-Id", TENANT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.overrides[?(@.capabilityCode=='audit')].effectiveEnabled").value(true))
                .andExpect(jsonPath("$.data.overrides[?(@.capabilityCode=='notice')].effectiveEnabled").value(false));
    }

    private UserAccount principal(String authority) {
        java.util.LinkedHashSet<String> authorities = new java.util.LinkedHashSet<>();
        authorities.add("upms:systenant:get");
        authorities.add("upms:systenant:edit");
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
}
