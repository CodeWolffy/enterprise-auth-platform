package com.enterprise.auth.platform.dept;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DeptControllerTest {

    private static final String SCOPE_USER = "dept_controller_scope_ut";
    private static final String CHILD_DEPT_CODE = "DEPT_CONTROLLER_CHILD_UT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long childDeptId;
    private Long scopeUserId;

    @BeforeEach
    void setUp() {
        childDeptId = ensureDept();
        scopeUserId = ensureUser();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", SCOPE_USER);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
    }

    @Test
    void listShouldApplyDataScope() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordEncoder.encode("DeptTest@123"),
                true,
                Set.of(),
                Set.of("dept:read"),
                Set.of(),
                DataScopeType.DEPT_AND_CHILDREN,
                1
        );

        mockMvc.perform(get("/api/depts")
                        .with(user(principal))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id==2)]").exists())
                .andExpect(jsonPath("$.data[?(@.id==" + childDeptId + ")]").exists())
                .andExpect(jsonPath("$.data[?(@.id==3)]").doesNotExist());
    }

    @Test
    void createShouldRejectHiddenParent() throws Exception {
        UserAccount principal = new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordEncoder.encode("DeptTest@123"),
                true,
                Set.of(),
                Set.of("dept:write"),
                Set.of(),
                DataScopeType.DEPT,
                1
        );

        mockMvc.perform(post("/api/depts")
                        .with(user(principal))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": 3,
                                  "deptCode": "DEPT_HIDDEN_PARENT_UT",
                                  "deptName": "越权部门",
                                  "leaderUserId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("无权使用该父级部门"));
    }

    private Long ensureDept() {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptCode(CHILD_DEPT_CODE);
        entity.setDeptName("部门控制器测试子部门");
        entity.setParentId(2L);
        sysDeptMapper.insert(entity);
        return entity.getId();
    }

    private Long ensureUser() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username = ?", "tenant-a", SCOPE_USER);
        SysUserEntity entity = new SysUserEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptId(2L);
        entity.setUsername(SCOPE_USER);
        entity.setDisplayName("部门控制器测试用户");
        entity.setPasswordHash(passwordEncoder.encode("DeptTest@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }
}
