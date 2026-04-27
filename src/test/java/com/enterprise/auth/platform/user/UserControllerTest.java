package com.enterprise.auth.platform.user;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.persistence.entity.SysDeptEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysDeptMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class UserControllerTest {

    private static final String SCOPE_USER = "user_controller_scope_ut";
    private static final String VISIBLE_USER = "user_controller_visible_ut";
    private static final String HIDDEN_USER = "user_controller_hidden_ut";
    private static final String CHILD_DEPT_CODE = "USER_CONTROLLER_CHILD_UT";

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

    private Long childDeptId;
    private Long scopeUserId;

    @BeforeEach
    void setUp() {
        childDeptId = ensureDept();
        scopeUserId = ensureUser(SCOPE_USER, 2L);
        ensureUser(VISIBLE_USER, childDeptId);
        ensureUser(HIDDEN_USER, 3L);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?, ?)",
                "tenant-a",
                SCOPE_USER,
                VISIBLE_USER,
                HIDDEN_USER
        );
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
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
                Set.of("user:read"),
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
                Set.of("user:write"),
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
                                  "username": "blocked_user_ut",
                                  "displayName": "Blocked User",
                                  "password": "UserTest@123",
                                  "deptId": 3,
                                  "enabled": true,
                                  "roleCodes": []
                                }
                                """))
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
                Set.of("user:write"),
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
                Set.of("user:write"),
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

    private Long ensureDept() {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptCode(CHILD_DEPT_CODE);
        entity.setDeptName("User Controller Child Dept");
        entity.setParentId(2L);
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
}
