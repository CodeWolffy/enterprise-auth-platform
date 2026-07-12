package com.enterprise.auth.platform.auth;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void protectedApiShouldRequireLoginBeforePermissionCheck() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void userReadEndpointShouldRequireUserReadPermission() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(bearer(principal(Set.of())))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/users")
                        .with(bearer(principal(Set.of("upms:sysuser:page"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void userWriteEndpointShouldRequireUserWriteBeforeValidation() throws Exception {
        String invalidPayload = """
                {
                  "username": "",
                  "displayName": "Invalid",
                  "password": "UserTest@123",
                  "deptId": 1,
                  "enabled": true,
                  "roleCodes": []
                }
                """;

        mockMvc.perform(post("/api/users")
                        .with(bearer(principal(Set.of("upms:sysuser:get"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/users")
                        .with(bearer(principal(Set.of("upms:sysuser:add"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void departmentReadAndWriteShouldUseSeparatePermissionKeys() throws Exception {
        mockMvc.perform(get("/api/depts")
                        .with(bearer(principal(Set.of("upms:sysdept:add"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/depts")
                        .with(bearer(principal(Set.of("upms:sysdept:page"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/depts")
                        .with(bearer(principal(Set.of("upms:sysdept:page"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": null,
                                  "deptCode": "AUTH_BOUNDARY_DEPT",
                                  "deptName": "",
                                  "leaderUserId": null
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void systemAndResourceWriteEndpointsShouldRequireSystemWritePermission() throws Exception {
        mockMvc.perform(post("/api/system/dicts")
                        .with(bearer(principal(Set.of("upms:system:get"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictType": "auth_boundary",
                                  "description": "Auth Boundary"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/menus")
                        .with(bearer(principal(Set.of("upms:system:get"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 20,
                                  "menuType": "MENU",
                                  "resourceKey": "auth.boundary",
                                  "menuName": "Auth Boundary",
                                  "routeKey": "auth-boundary",
                                  "grantKey": "upms:session:get",
                                  "path": "/auth-boundary",
                                  "component": "AuthBoundaryView",
                                  "icon": null,
                                  "orderNo": 999,
                                  "visible": true,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void permissionSnapshotShouldExposeStableMenuAndGrantContract() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(bearer(principal(1L, Set.of("ADMIN"), Set.of())))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.operatorTenantId").value("platform"))
                .andExpect(jsonPath("$.data.roles[?(@ == 'ADMIN')]").exists())
                .andExpect(jsonPath("$.data.grants[?(@ == 'upms:sysuser:page')]").exists())
                .andExpect(jsonPath("$.data.menus").isArray())
                .andExpect(jsonPath("$.data.menus..path").exists())
                .andExpect(jsonPath("$.data.menus..component").exists())
                .andExpect(jsonPath("$.data.superAdmin").value(true));
    }

  @Test
  void allScopeSessionsShouldFallBackToOwnSessionsWithoutSessionWritePermission() throws Exception {
    mockMvc.perform(get("/api/auth/sessions")
        .queryParam("scope", "all")
        .with(bearer(principal(1L, Set.of("upms:session:get"))))
        .header("X-Tenant-Id", "platform"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records[?(@.currentSession==true)]").exists());
  }

    private UserAccount principal(Set<String> permissions) {
        return principal(1L, permissions);
    }

    private UserAccount principal(long userId, Set<String> permissions) {
        return principal(userId, Set.of(), permissions);
    }

    private UserAccount principal(long userId, Set<String> roles, Set<String> permissions) {
        Integer sessionVersion = jdbcTemplate.queryForObject(
                "SELECT session_version FROM sys_user WHERE id = ?",
                Integer.class,
                userId
        );
        return new UserAccount(
                userId,
                "platform",
                "authorization_boundary_user",
                passwordHasher.hash("Boundary@123"),
                true,
                roles,
                permissions,
                Set.of(),
                DataScopeType.ALL,
                sessionVersion == null ? 1 : sessionVersion
        );
    }
}
