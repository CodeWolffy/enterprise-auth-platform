package com.enterprise.auth.platform.resource;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
    private static final String PLATFORM_USER = "resource_auth_platform_user_ut";
    private static final String TENANT_USER = "resource_auth_tenant_user_ut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        tearDown();
    }

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
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN (?, ?)", PLATFORM_USER, TENANT_USER);
    }

    @Test
    void updateResourceShouldRejectDescendantAsParent() throws Exception {
        mockMvc.perform(put("/api/resources/{resourceId}", 20L)
                        .with(bearer(principal("platform", Set.of("system:write"))))
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
    void createMenuShouldRequireRouteAndGrantFields() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .with(bearer(principal("platform", Set.of("system:write"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "resourceType": "MENU",
                                  "resourceKey": "ut.missing.route",
                                  "resourceName": "UT Missing Route",
                                  "routeKey": null,
                                  "grantKey": null,
                                  "path": null,
                                  "component": null,
                                  "icon": null,
                                  "orderNo": 999,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createButtonShouldRequireMenuParentAndGrantKey() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .with(bearer(principal("platform", Set.of("system:write"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "resourceType": "BUTTON",
                                  "resourceKey": "ut.button.invalid.parent",
                                  "resourceName": "UT Invalid Button",
                                  "routeKey": null,
                                  "grantKey": null,
                                  "path": null,
                                  "component": null,
                                  "icon": null,
                                  "orderNo": 999,
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
                        .with(bearer(principal("tenant-a", Set.of("role:write"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceIds": [24]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==30)]").exists())
                .andExpect(jsonPath("$.data[?(@==24)]").exists());

        mockMvc.perform(get("/api/roles/{roleId}/resources", roleId)
                        .with(bearer(principal("tenant-a", Set.of("role:read"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==30)]").exists())
                .andExpect(jsonPath("$.data[?(@==24)]").exists());
    }

    @Test
    void assignMenuShouldNotGrantChildButtonsUnlessSelected() throws Exception {
        Long roleId = createTempRole("tenant-a");

        mockMvc.perform(put("/api/roles/{roleId}/resources", roleId)
                        .with(bearer(principal("tenant-a", Set.of("role:write"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceIds": [21]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists());

        List<Long> assignedIds = jdbcTemplate.queryForList(
                "SELECT resource_id FROM sys_role_resource WHERE tenant_id = ? AND role_id = ?",
                Long.class,
                "tenant-a",
                roleId
        );
        assertThat(assignedIds).contains(1L, 20L, 21L);
        assertThat(assignedIds).doesNotContain(210L, 211L, 212L);
    }

    @Test
    void assignButtonShouldAutoFillAncestorMenuOnly() throws Exception {
        Long roleId = createTempRole("tenant-a");

        mockMvc.perform(put("/api/roles/{roleId}/resources", roleId)
                        .with(bearer(principal("tenant-a", Set.of("role:write"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceIds": [210]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists())
                .andExpect(jsonPath("$.data[?(@==210)]").exists());

        List<Long> assignedIds = jdbcTemplate.queryForList(
                "SELECT resource_id FROM sys_role_resource WHERE tenant_id = ? AND role_id = ?",
                Long.class,
                "tenant-a",
                roleId
        );
        assertThat(assignedIds).contains(1L, 20L, 21L, 210L);
        assertThat(assignedIds).doesNotContain(211L, 212L);
    }

    @Test
    void tenantResourceOverridesShouldUpdateAndQuery() throws Exception {
        mockMvc.perform(put("/api/tenants/{tenantId}/resource-overrides", "tenant-a")
                        .with(bearer(principal("platform", Set.of("tenant:write"))))
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
                        .with(bearer(principal("platform", Set.of("tenant:read"))))
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
        String username = "platform".equals(tenantId) ? PLATFORM_USER : TENANT_USER;
        Long userId = ensureLoginUser(tenantId, username);
        LinkedHashSet<String> grants = new LinkedHashSet<>(permissions);
        return new UserAccount(
                userId,
                tenantId,
                username,
                "{noop}ignored",
                true,
                Set.of("ADMIN"),
                grants,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }

    private Long ensureLoginUser(String tenantId, String username) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = ?", username);
        jdbcTemplate.update(
                """
                INSERT INTO sys_user (
                    tenant_id, dept_id, username, password_hash, enabled, session_version,
                    display_name, created_by, updated_by, deleted
                ) VALUES (?, ?, ?, ?, 1, 1, ?, ?, ?, 0)
                """,
                tenantId,
                null,
                username,
                "{noop}ignored",
                username,
                "tester",
                "tester"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?",
                Long.class,
                username
        );
    }
}
