package com.enterprise.auth.platform.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ResourceAuthorizationControllerTest {

    private static final String TEMP_ROLE_PREFIX = "RESOURCE_AUTH_V2_UT_";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        List<Long> roleIds = jdbcTemplate.queryForList(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code LIKE ?",
                Long.class,
                "tenant-a",
                TEMP_ROLE_PREFIX + "%"
        );
        if (!roleIds.isEmpty()) {
            String inClause = roleIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
            jdbcTemplate.update("DELETE FROM sys_role_resource WHERE tenant_id = ? AND role_id IN (" + inClause + ")", "tenant-a");
            jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND id IN (" + inClause + ")", "tenant-a");
        }
        jdbcTemplate.update(
                "DELETE FROM sys_tenant_resource_override WHERE tenant_id = ? AND resource_id = ?",
                "tenant-a",
                25L
        );
    }

    @Test
    void updateResourceShouldRejectDescendantAsParent() throws Exception {
        mockMvc.perform(put("/api/resources/{resourceId}", 20L)
                        .with(user(principal("platform", Set.of("system:write"))))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 21,
                                  "resourceType": "DIR",
                                  "resourceKey": "system",
                                  "resourceName": "系统模块",
                                  "routeKey": null,
                                  "grantKey": null,
                                  "path": null,
                                  "component": null,
                                  "icon": "Setting",
                                  "orderNo": 20,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void assignRoleResourcesShouldAutoFillAncestors() throws Exception {
        Long roleId = createTempRole("tenant-a");

        mockMvc.perform(put("/api/roles/{roleId}/resources", roleId)
                        .with(user(principal("tenant-a", Set.of("role:write"))))
                        .with(csrf())
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceIds": [25]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==25)]").exists());

        mockMvc.perform(get("/api/roles/{roleId}/resources", roleId)
                        .with(user(principal("tenant-a", Set.of("role:read"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==25)]").exists());
    }

    @Test
    void tenantResourceOverridesShouldUpdateAndQuery() throws Exception {
        mockMvc.perform(put("/api/tenants/{tenantId}/resource-overrides", "tenant-a")
                        .with(user(principal("platform", Set.of("tenant:write"))))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "overrides": [
                                    {
                                      "resourceId": 25,
                                      "enabled": false,
                                      "visible": false,
                                      "orderNo": 777,
                                      "titleOverride": "审计中心",
                                      "iconOverride": "Histogram"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        Integer enabled = jdbcTemplate.queryForObject(
                "SELECT enabled FROM sys_tenant_resource_override WHERE tenant_id = ? AND resource_id = ?",
                Integer.class,
                "tenant-a",
                25L
        );
        Integer visible = jdbcTemplate.queryForObject(
                "SELECT visible FROM sys_tenant_resource_override WHERE tenant_id = ? AND resource_id = ?",
                Integer.class,
                "tenant-a",
                25L
        );
        assertThat(enabled).isEqualTo(0);
        assertThat(visible).isEqualTo(0);

        mockMvc.perform(get("/api/tenants/{tenantId}/resource-overrides", "tenant-a")
                        .with(user(principal("platform", Set.of("tenant:read"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.resourceId==25)]").exists());
    }

    private Long createTempRole(String tenantId) {
        String roleCode = TEMP_ROLE_PREFIX + System.currentTimeMillis();
        jdbcTemplate.update(
                """
                INSERT INTO sys_role (
                    tenant_id, role_code, role_name, data_scope_type, role_desc, data_scope_value_json,
                    created_by, updated_by, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                tenantId,
                roleCode,
                "Resource Auth V2 Test Role",
                "ALL",
                "ut",
                null,
                "tester",
                "tester"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                Long.class,
                tenantId,
                roleCode
        );
    }

    private UserAccount principal(String tenantId, Set<String> permissions) {
        LinkedHashSet<String> grants = new LinkedHashSet<>(permissions);
        return new UserAccount(
                1L,
                tenantId,
                "tester",
                "{noop}ignored",
                true,
                Set.of("ADMIN"),
                grants,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
