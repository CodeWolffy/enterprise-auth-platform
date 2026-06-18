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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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
        jdbcTemplate.update("DELETE FROM sys_menu WHERE permission LIKE ?", "upms:utbatch:%");
        jdbcTemplate.update("DELETE FROM sys_menu WHERE permission LIKE ?", "ut:batch:%");
        jdbcTemplate.update("DELETE FROM sys_menu WHERE permission = ?", "upms:resourceauth:reuse");
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
                                  "type": "0",
                                  "name": "系统模块",
                                  "permission": null,
                                  "path": null,
                                  "component": null,
                                  "redirect": null,
                                  "icon": "Setting",
                                  "sort": 20,
                                  "outerStatus": false,
                                  "applicationKey": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("系统节点不允许修改父节点"));
    }

    @Test
    void createMenuShouldRejectDuplicatePath() throws Exception {
        mockMvc.perform(post("/api/menus")
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:add"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "type": "0",
                                  "name": "UT Duplicate Route",
                                  "permission": null,
                                  "path": "/system/users",
                                  "component": "UsersView",
                                  "redirect": null,
                                  "icon": null,
                                  "sort": 999,
                                  "outerStatus": false,
                                  "applicationKey": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("访问路径已存在"));
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
                                  "type": "0",
                                  "name": "系统模块",
                                  "permission": null,
                                  "path": null,
                                  "component": null,
                                  "redirect": null,
                                  "icon": "Setting",
                                  "sort": 20,
                                  "outerStatus": false,
                                  "applicationKey": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createButtonShouldRejectPermissionOnMenuNode() throws Exception {
        mockMvc.perform(post("/api/menus")
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:add"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "type": "0",
                                  "name": "UT Menu With Permission",
                                  "permission": "upms:sysuser:get",
                                  "path": "/ut/menu-with-permission",
                                  "component": "UsersView",
                                  "redirect": null,
                                  "icon": null,
                                  "sort": 999,
                                  "outerStatus": false,
                                  "applicationKey": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createButtonShouldEvictPermissionSnapshots() throws Exception {
        AtomicReference<String> tokenRef = new AtomicReference<>();

        mockMvc.perform(post("/api/menus")
                        .with(bearerWithSnapshotCapture(principal("platform", Set.of("upms:sysmenu:add")), tokenRef))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 21,
                                  "type": "1",
                                  "name": "UT Reuse Button",
                                  "permission": "upms:resourceauth:reuse",
                                  "path": null,
                                  "component": null,
                                  "redirect": null,
                                  "icon": null,
                                  "sort": 999,
                                  "outerStatus": false,
                                  "applicationKey": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permission").value("upms:resourceauth:reuse"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE type = '1' AND permission = ?",
                Long.class,
                "upms:resourceauth:reuse"
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
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:add"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actions": ["page", "add"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='UT 批量按钮菜单列表')]").exists())
                .andExpect(jsonPath("$.data[?(@.permission=='upms:utbatch:read:page')]").exists())
                .andExpect(jsonPath("$.data[?(@.name=='UT 批量按钮菜单新增')]").exists())
                .andExpect(jsonPath("$.data[?(@.permission=='upms:utbatch:read:add')]").exists());

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE parent_id = ? AND type = '1' AND permission LIKE ?",
                Long.class,
                menuId,
                "upms:utbatch:read:%"
        );
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void deleteMenuShouldRejectRoleBindings() throws Exception {
        Long menuId = createTempMenu();
        Long roleId = createTempRole("tenant-a");
        jdbcTemplate.update(
                "INSERT INTO sys_role_menu(tenant_id, role_id, menu_id, create_time) VALUES(?, ?, ?, NOW())",
                "tenant-a",
                roleId,
                menuId
        );

        mockMvc.perform(delete("/api/menus/{menuId}", menuId)
                        .with(bearer(principal("platform", Set.of("upms:sysmenu:del"))))
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
                        .with(bearer(principal("tenant-a", Set.of("upms:sysmenu:add"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 21,
                                  "type": "1",
                                  "name": "UT Tenant Boundary",
                                  "permission": "upms:sysuser:get",
                                  "path": null,
                                  "component": null,
                                  "redirect": null,
                                  "icon": null,
                                  "sort": 999,
                                  "outerStatus": false,
                                  "applicationKey": null
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
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists());

        mockMvc.perform(get("/api/roles/{roleId}/menus", roleId)
                        .with(bearer(principal("tenant-a", Set.of("upms:sysrole:get"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists());
    }

    @Test
    void assignMenuShouldNotGrantChildButtonsUnlessSelected() throws Exception {
        Long roleId = createTempRole("tenant-a");
        List<Long> childButtonIds = childButtonIdsUnder(21L);

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
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists());

        List<Long> assignedIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE tenant_id = ? AND role_id = ?",
                Long.class,
                "tenant-a",
                roleId
        );
        assertThat(assignedIds).contains(20L, 21L);
        assertThat(assignedIds).doesNotContain(childButtonIds.toArray(new Long[0]));
    }

    @Test
    void assignButtonShouldAutoFillAncestorMenuOnly() throws Exception {
        Long roleId = createTempRole("tenant-a");
        Long buttonId = firstChildButtonIdUnder(21L);
        List<Long> siblingButtonIds = childButtonIdsUnder(21L).stream()
                .filter(id -> !id.equals(buttonId))
                .toList();

        mockMvc.perform(put("/api/roles/{roleId}/menus", roleId)
                        .with(bearer(principal("tenant-a", Set.of("upms:sysrole:edit"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "menuIds": [%d]
                                }
                                """.formatted(buttonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@==20)]").exists())
                .andExpect(jsonPath("$.data[?(@==21)]").exists())
                .andExpect(jsonPath("$.data[?(@==%d)]".formatted(buttonId)).exists());

        List<Long> assignedIds = jdbcTemplate.queryForList(
                "SELECT menu_id FROM sys_role_menu WHERE tenant_id = ? AND role_id = ?",
                Long.class,
                "tenant-a",
                roleId
        );
        assertThat(assignedIds).contains(20L, 21L, buttonId);
        assertThat(assignedIds).doesNotContain(siblingButtonIds.toArray(new Long[0]));
    }

    private List<Long> childButtonIdsUnder(Long parentId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM sys_menu WHERE parent_id = ? AND type = '1' AND del_flag = '0' ORDER BY id",
                Long.class,
                parentId
        );
    }

    private Long firstChildButtonIdUnder(Long parentId) {
        List<Long> ids = childButtonIdsUnder(parentId);
        if (ids.isEmpty()) {
            throw new IllegalStateException("No button found under menu " + parentId);
        }
        return ids.get(0);
    }

    private Long createTempMenu() {
        jdbcTemplate.update("DELETE FROM sys_menu WHERE permission LIKE ?", "upms:utbatch:%");
        jdbcTemplate.update("DELETE FROM sys_menu WHERE permission LIKE ?", "ut:batch:%");
        jdbcTemplate.update(
                """
                INSERT INTO sys_menu (
                    parent_id, name, permission, path, component, icon, sort, type,
                    outer_status, application_key, del_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, '0', 0, ?, '0')
                """,
                20L,
                "UT 批量按钮菜单",
                "upms:utbatch:read",
                "/ut/batch-actions",
                "UtBatchActionsView",
                null,
                998,
                "app_ut"
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE name = ?",
                Long.class,
                "UT 批量按钮菜单"
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
