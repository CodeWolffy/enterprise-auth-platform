package com.enterprise.auth.platform.dept;

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
import com.enterprise.auth.platform.security.PasswordHasher;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeptControllerTest {

    private static final String SCOPE_USER = "dept_controller_scope_ut";
    private static final String SCOPE_ROLE = "DEPT_CONTROLLER_SCOPE_ROLE_UT";
    private static final String PARENT_DEPT_CODE = "DEPT_CONTROLLER_PARENT_UT";
    private static final String CHILD_DEPT_CODE = "DEPT_CONTROLLER_CHILD_UT";
    private static final String HIDDEN_DEPT_CODE = "DEPT_CONTROLLER_HIDDEN_UT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

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
        parentDeptId = ensureDept(PARENT_DEPT_CODE, "部门控制器测试父部门", null);
        childDeptId = ensureDept(CHILD_DEPT_CODE, "部门控制器测试子部门", parentDeptId);
        hiddenDeptId = ensureDept(HIDDEN_DEPT_CODE, "部门控制器测试隐藏部门", null);
        scopeUserId = ensureUser();
        ensureDeptScopeRole(scopeUserId);
        clearCaches();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", "tenant-a", scopeUserId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", "tenant-a", SCOPE_ROLE);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", SCOPE_USER);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code IN (?, ?, ?)", "tenant-a", PARENT_DEPT_CODE, CHILD_DEPT_CODE, HIDDEN_DEPT_CODE);
        clearCaches();
    }

    @Test
    void listShouldApplyDataScope() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("DeptTest@123"),
                true,
                Set.of(),
                Set.of("dept:read"),
                Set.of(),
                DataScopeType.DEPT_AND_CHILDREN,
                1
        );

        mockMvc.perform(get("/api/depts")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==" + parentDeptId + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id==" + childDeptId + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id==" + hiddenDeptId + ")]").doesNotExist());
    }

    @Test
    void createShouldRejectHiddenParent() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("DeptTest@123"),
                true,
                Set.of(),
                Set.of("dept:write"),
                Set.of(),
                DataScopeType.DEPT,
                1
        );

        mockMvc.perform(post("/api/depts")
                        .with(bearer(principal))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": %d,
                                  "deptCode": "DEPT_HIDDEN_PARENT_UT",
                                  "deptName": "越权部门",
                                  "leaderUserId": null
                                }
                                """.formatted(hiddenDeptId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("无权使用该父级部门"));
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

    private Long ensureUser() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", SCOPE_USER);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptId(parentDeptId);
        entity.setUsername(SCOPE_USER);
        entity.setDisplayName("部门控制器测试用户");
        entity.setPasswordHash(passwordHasher.hash("DeptTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }

    private void ensureDeptScopeRole(Long userId) {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", "tenant-a", userId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", "tenant-a", SCOPE_ROLE);
        jdbcTemplate.update(
                "INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope_type, deleted, created_at, updated_at) VALUES(?,?,?,?,0,NOW(),NOW())",
                "tenant-a", SCOPE_ROLE, "部门控制器测试角色", "DEPT_AND_CHILDREN"
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
