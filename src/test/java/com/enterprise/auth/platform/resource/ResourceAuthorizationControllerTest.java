package com.enterprise.auth.platform.resource;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.auth.infrastructure.AuthPrincipalCacheService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
        "app.security.redis.session-enabled=false"
})
@AutoConfigureMockMvc
class ResourceAuthorizationControllerTest {

    private static final String TEMP_ROLE_PREFIX = "RESOURCE_AUTH_V2_UT_";
    private static final String PLATFORM_USER = "resource_auth_platform_user_ut";
    private static final String TENANT_USER = "resource_auth_tenant_user_ut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private AuthPrincipalCacheService authPrincipalCacheService;

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
            jdbcTemplate.update("DELETE FROM sys_role_menu WHERE tenant_id = ? AND role_id IN (" + inClause + ")", "tenant-a");
            jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND id IN (" + inClause + ")", "tenant-a");
        }
        jdbcTemplate.update("DELETE FROM sys_user WHERE username IN (?, ?)", PLATFORM_USER, TENANT_USER);
        jdbcTemplate.update(
                "DELETE FROM sys_menu WHERE tenant_id = ? AND resource_key LIKE ?",
                "platform",
                "ut.menu.%"
        );
        jdbcTemplate.update(
                "DELETE FROM sys_menu WHERE tenant_id = ? AND resource_key LIKE ?",
                "platform",
                "ut.batch.%"
        );
    }

    @Test
    void updateSystemMenuShouldRejectIdentityFieldChanges() throws Exception {
        mockMvc.perform(put("/api/menus/{menuId}", 20L)
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 1,
                                  "menuType": "DIR",
                                  "resourceKey": "system-renamed",
                                  "menuName": "系统模块",
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
                .andExpect(jsonPath("$.message").value("系统节点不允许修改资源唯一标识"));
    }

    @Test
    void createMenuShouldRejectDuplicateRouteKey() throws Exception {
        mockMvc.perform(post("/api/menus")
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "menuType": "MENU",
                                  "resourceKey": "ut.duplicate.route",
                                  "menuName": "UT Duplicate Route",
                                  "routeKey": "users",
                                  "grantKey": "upms:sysuser:get",
                                  "path": "/ut/duplicate-route",
                                  "component": "UsersView",
                                  "icon": null,
                                  "orderNo": 999,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("路由标识已存在"));
    }

    @Test
    void updateMenuShouldRejectDescendantAsParent() throws Exception {
        mockMvc.perform(put("/api/menus/{menuId}", 20L)
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 21,
                                  "menuType": "DIR",
                                  "resourceKey": "system",
                                  "menuName": "系统模块",
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
        mockMvc.perform(post("/api/menus")
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "menuType": "MENU",
                                  "resourceKey": "ut.missing.route",
                                  "menuName": "UT Missing Route",
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
        mockMvc.perform(post("/api/menus")
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "menuType": "BUTTON",
                                  "resourceKey": "ut.button.invalid.parent",
                                  "menuName": "UT Invalid Button",
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
    void createMenuShouldAllowGrantKeyReuseAndEvictPermissionSnapshots() throws Exception {
        AtomicReference<String> tokenRef = new AtomicReference<>();

        mockMvc.perform(post("/api/menus")
                        .with(bearerWithSnapshotCapture(principal("platform", Set.of("upms:sysmenu:edit", "stale:grant")), tokenRef))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 21,
                                  "menuType": "BUTTON",
                                  "resourceKey": "ut.menu.reuse.button",
                                  "menuName": "UT Reuse Button",
                                  "routeKey": null,
                                  "grantKey": "upms:sysuser:get",
                                  "path": null,
                                  "component": null,
                                  "icon": null,
                                  "orderNo": 999,
                                  "visible": false,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grantKey").value("upms:sysuser:get"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND resource_key = ? AND grant_key = ?",
                Long.class,
                "platform",
                "ut.menu.reuse.button",
                "upms:sysuser:get"
        );
        assertThat(count).isEqualTo(1L);
        verify(authPrincipalCacheService).evictAll();

        SaSession tokenSession = StpUtil.getTokenSessionByToken(tokenRef.get());
        assertThat(tokenSession.get("permissions")).isNull();
        assertThat(tokenSession.get("permissionsTenantId")).isNull();
        assertThat(tokenSession.get("roles")).isNull();
    }

    @Test
    void batchCreateActionsShouldCreateButtonNodes() throws Exception {
        Long menuId = createTempMenu();

        mockMvc.perform(post("/api/menus/{menuId}/actions", menuId)
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actions": ["read", "create"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.resourceKey=='upms:ut.menu.batch.actions:read')]").exists())
                .andExpect(jsonPath("$.data[?(@.grantKey=='upms:utbatch:read')]").exists())
                .andExpect(jsonPath("$.data[?(@.resourceKey=='ut.menu.batch.actions:create')]").exists())
                .andExpect(jsonPath("$.data[?(@.grantKey=='utbatch:create')]").exists());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE tenant_id = ? AND parent_id = ? AND resource_key LIKE ?",
                Long.class,
                "platform",
                menuId,
                "ut.menu.batch.actions:%"
        );
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void deleteMenuShouldRejectRoleBindings() throws Exception {
        Long menuId = createTempMenu();
        Long roleId = createTempRole("tenant-a");
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu(tenant_id, role_id, menu_id, created_at) VALUES(?, ?, ?, NOW())",
                "tenant-a",
                roleId,
                menuId
        );

        mockMvc.perform(delete("/api/menus/{menuId}", menuId)
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("菜单已被角色授权引用，暂不允许删除"));
    }

    private RequestPostProcessor bearerWithSnapshotCapture(UserAccount user, AtomicReference<String> tokenRef) {
        return request -> {
            String token = StpUtil.createLoginSession(user.id(), new SaLoginModel().setDevice("mockmvc"));
            SaSession tokenSession = StpUtil.getTokenSessionByToken(token);
            tokenSession.set("username", user.username());
            tokenSession.set("userId", user.id());
            tokenSession.set("tenantId", user.tenantId());
            tokenSession.set("activeTenantId", user.tenantId());
            tokenSession.set("sessionVersion", user.sessionVersion());
            tokenSession.set("roles", List.copyOf(user.roles()));
            tokenSession.set("permissions", List.copyOf(user.permissions()));
            tokenSession.set("permissionsTenantId", user.tenantId());
            tokenSession.set("clientIp", "127.0.0.1");
            tokenSession.set("device", "mockmvc");
            long now = System.currentTimeMillis();
            tokenSession.set("issuedAt", now);
            tokenSession.set("expiresAt", now + 7 * 24 * 60 * 60 * 1000L);
            tokenRef.set(token);
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    @Test
    void tenantCannotMutatePlatformMenuTemplate() throws Exception {
        mockMvc.perform(post("/api/menus")
                        .with(bearer(principal("tenant-a", Set.of("upms:sysmenu:edit"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 21,
                                  "menuType": "BUTTON",
                                  "resourceKey": "ut.menu.tenant.boundary",
                                  "menuName": "UT Tenant Boundary",
                                  "routeKey": null,
                                  "grantKey": "upms:sysuser:get",
                                  "path": null,
                                  "component": null,
                                  "icon": null,
                                  "orderNo": 999,
                                  "visible": false,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("仅平台租户允许维护菜单模板"));
    }

    @Test
    void assignRoleMenusShouldAutoFillAncestors() throws Exception {
        Long roleId = createTempRole("tenant-a");

        mockMvc.perform(put("/api/roles/{roleId}/menus", roleId)
                        .with(bearer(principal("tenant-a", Set.of("upms:sysrole:edit"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "menuIds": [21]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists());

        mockMvc.perform(get("/api/roles/{roleId}/menus", roleId)
                        .with(bearer(principal("tenant-a", Set.of("upms:sysrole:get"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists());
    }

    @Test
    void assignMenuShouldNotGrantChildButtonsUnlessSelected() throws Exception {
        Long roleId = createTempRole("tenant-a");

        mockMvc.perform(put("/api/roles/{roleId}/menus", roleId)
                        .with(bearer(principal("tenant-a", Set.of("upms:sysrole:edit"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "menuIds": [21]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists());

        List<Long> assignedIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE tenant_id = ? AND role_id = ?",
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

        mockMvc.perform(put("/api/roles/{roleId}/menus", roleId)
                        .with(bearer(principal("tenant-a", Set.of("upms:sysrole:edit"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "menuIds": [210]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==1)]").exists())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists())
                .andExpect(jsonPath("$.data[?(@==210)]").exists());

        List<Long> assignedIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE tenant_id = ? AND role_id = ?",
                Long.class,
                "tenant-a",
                roleId
        );
        assertThat(assignedIds).contains(1L, 20L, 21L, 210L);
        assertThat(assignedIds).doesNotContain(211L, 212L);
    }

    private Long createTempMenu() {
        jdbcTemplate.update("DELETE FROM sys_menu WHERE tenant_id = ? AND resource_key LIKE ?", "platform", "ut.menu.batch.actions%");
        jdbcTemplate.update(
                """
                INSERT INTO sys_menu (
                    tenant_id, parent_id, ancestors, menu_type, resource_key, menu_name, route_key,
                    grant_key, path, component, icon, order_no, visible, enabled, is_system,
                    outer_status, application_key, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0, 0, ?, 0)
                """,
                "platform",
                20L,
                "1,20",
                "MENU",
                "ut.menu.batch.actions",
                "UT 批量按钮菜单",
                "ut-batch-actions",
                "upms:utbatch:read",
                "/ut/batch-actions",
                "UtBatchActionsView",
                null,
                998,
                "app_ut"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE tenant_id = ? AND resource_key = ?",
                Long.class,
                "platform",
                "ut.menu.batch.actions"
        );
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
