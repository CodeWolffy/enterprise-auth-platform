package com.enterprise.auth.platform.user;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import com.enterprise.auth.platform.modules.auth.domain.PasswordHasher;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    private static final String SCOPE_USER = "uc_scope_ut";
    private static final String VISIBLE_USER = "uc_visible_ut";
    private static final String HIDDEN_USER = "uc_hidden_ut";
    private static final String SCOPE_ROLE = "USER_CONTROLLER_SCOPE_ROLE_UT";
    private static final String PARENT_DEPT_CODE = "USER_CONTROLLER_PARENT_UT";
    private static final String CHILD_DEPT_CODE = "USER_CONTROLLER_CHILD_UT";
    private static final String HIDDEN_DEPT_CODE = "USER_CONTROLLER_HIDDEN_UT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    private Long parentDeptId;
    private Long childDeptId;
    private Long hiddenDeptId;
    private Long scopeUserId;

    @BeforeEach
    void setUp() {
        clearCaches();
        parentDeptId = ensureDept(PARENT_DEPT_CODE, "User Controller Parent Dept", null);
        childDeptId = ensureDept(CHILD_DEPT_CODE, "User Controller Child Dept", parentDeptId);
        hiddenDeptId = ensureDept(HIDDEN_DEPT_CODE, "User Controller Hidden Dept", null);
        scopeUserId = ensureUser(SCOPE_USER, parentDeptId);
        ensureUser(VISIBLE_USER, childDeptId);
        ensureUser(HIDDEN_USER, hiddenDeptId);
        ensureUserScopeRole(scopeUserId);
        clearCaches();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", "tenant-a", scopeUserId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", "tenant-a", SCOPE_ROLE);
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?, ?)",
                "tenant-a",
                SCOPE_USER,
                VISIBLE_USER,
                HIDDEN_USER
        );
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code IN (?, ?, ?)", "tenant-a", PARENT_DEPT_CODE, CHILD_DEPT_CODE, HIDDEN_DEPT_CODE);
        clearCaches();
    }

    @Test
    void listShouldApplyDataScope() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("UserTest@123"),
                true,
                Set.of(),
                Set.of("upms:sysuser:page"),
                Set.of(),
                DataScopeType.DEPT_AND_CHILDREN,
                1
        );

        mockMvc.perform(get("/api/users")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.username=='" + SCOPE_USER + "')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.username=='" + VISIBLE_USER + "')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.username=='" + HIDDEN_USER + "')]").doesNotExist());
    }

    @Test
    void createShouldRejectHiddenDepartment() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("UserTest@123"),
                true,
                Set.of(),
                Set.of("upms:sysuser:add"),
                Set.of(),
                DataScopeType.DEPT,
                1
        );

        mockMvc.perform(post("/api/users")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "blocked_ut",
                                  "displayName": "Blocked User",
                                  "password": "UserTest@123",
                                  "deptId": %d,
                                  "enabled": true,
                                  "roleCodes": []
                                }
                                """.formatted(hiddenDeptId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createShouldReturnValidationErrorCode() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("UserTest@123"),
                true,
                Set.of(),
                Set.of("upms:sysuser:add"),
                Set.of(),
                DataScopeType.DEPT,
                1
        );

        mockMvc.perform(post("/api/users")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "displayName": "Invalid Payload",
                                  "password": "UserTest@123",
                                  "deptId": 2,
                                  "enabled": true,
                                  "roleCodes": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createShouldRejectDuplicateUsernameInsideCurrentTenant() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("UserTest@123"),
                true,
                Set.of(),
                Set.of("upms:sysuser:add"),
                Set.of(),
                DataScopeType.DEPT,
                1
        );

        mockMvc.perform(post("/api/users")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "displayName": "Duplicate User",
                                  "password": "UserTest@123",
                                  "deptId": 2,
                                  "enabled": true,
                                  "roleCodes": []
                                }
                                """.formatted(VISIBLE_USER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void listShouldReturnAccessDeniedCodeWhenAuthorityMissing() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("UserTest@123"),
                true,
                Set.of(),
                Set.of(),
                Set.of(),
                DataScopeType.DEPT,
                1
        );

        mockMvc.perform(get("/api/users")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").exists());
    }

    private Long ensureDept(String deptCode, String deptName, Long parentId) {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", deptCode);
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptCode(deptCode);
        entity.setDeptName(deptName);
        entity.setParentId(parentId);
        sysDeptMapper.insert(entity);
        return entity.getId();
    }

    private Long ensureUser(String username, Long deptId) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", username);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptId(deptId);
        entity.setUsername(username);
        entity.setDisplayName(username);
        entity.setPasswordHash(passwordHasher.hash("UserTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }

    private void ensureUserScopeRole(Long userId) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", "tenant-a", userId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", "tenant-a", SCOPE_ROLE);
        jdbcTemplate.update(
                "INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope_type, deleted, created_at, updated_at) VALUES(?,?,?,?,0,NOW(),NOW())",
                "tenant-a", SCOPE_ROLE, "用户控制器测试角色", "DEPT_AND_CHILDREN"
        );
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                Long.class,
                "tenant-a",
                SCOPE_ROLE
        );
        jdbcTemplate.update(
                "INSERT INTO sys_user_role(tenant_id, user_id, role_id, created_at, updated_at) VALUES(?,?,?,?,?)",
                "tenant-a", userId, roleId, java.time.LocalDateTime.now(), java.time.LocalDateTime.now()
        );
    }

    private void clearCaches() {
        var cache = cacheManager.getCache("auth:principal");
        if (cache != null) {
            cache.clear();
        }
    }
}
