package com.enterprise.auth.platform.tenant;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.dto.model.DataScopeType;
import com.enterprise.auth.platform.dto.model.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.security.PasswordHasher;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TenantCatalogControllerTest {

    private static final String CAPABILITY_CODE = "tenant_capability_ut";
    private static final String PACKAGE_CODE = "tenant_package_ut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHasher passwordHasher;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_tenant_package_capability WHERE tenant_id = 'platform' AND package_code = ?", PACKAGE_CODE);
        jdbcTemplate.update("DELETE FROM sys_tenant_package WHERE tenant_id = 'platform' AND package_code = ?", PACKAGE_CODE);
        jdbcTemplate.update("DELETE FROM sys_tenant_capability WHERE tenant_id = 'platform' AND capability_code = ?", CAPABILITY_CODE);
    }

    @Test
    void shouldManagePlatformTenantCatalog() throws Exception {
        UserAccount principal = principal(Set.of("tenant:read", "tenant:write"));

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
                                  "userQuota": 50,
                                  "storageQuotaGb": 20,
                                  "packageDesc": "用于租户目录接口测试",
                                  "enabled": true,
                                  "capabilityCodes": ["tenant_capability_ut"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageCode").value(PACKAGE_CODE))
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

        mockMvc.perform(get("/api/tenant-catalog/capabilities")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.capabilityCode=='" + CAPABILITY_CODE + "')]").exists());

        mockMvc.perform(get("/api/tenant-catalog/packages")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.packageCode=='" + PACKAGE_CODE + "')]").exists());

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
                                  "userQuota": 88,
                                  "storageQuotaGb": 30,
                                  "packageDesc": "用于租户目录接口测试更新",
                                  "enabled": true,
                                  "capabilityCodes": ["tenant_capability_ut"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageName").value("测试套餐已更新"));

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
