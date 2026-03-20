package com.enterprise.auth.platform.system;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class SystemControllerTest {

    private static final String SCOPE_USER = "system_scope_user_ut";
    private static final String VISIBLE_USER = "system_visible_user_ut";
    private static final String HIDDEN_USER = "system_hidden_user_ut";
    private static final String CHILD_DEPT_CODE = "SYSTEM_SCOPE_CHILD_UT";
    private static final String VISIBLE_DICT_CODE = "SYSTEM_VISIBLE_DICT_UT";
    private static final String HIDDEN_DICT_CODE = "SYSTEM_HIDDEN_DICT_UT";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    private Long scopeUserId;
    private Long hiddenDictId;

    @BeforeEach
    void setUp() {
        Long childDeptId = ensureDept();
        scopeUserId = ensureUser(SCOPE_USER, 2L);
        ensureUser(VISIBLE_USER, childDeptId);
        ensureUser(HIDDEN_USER, 3L);

        jdbcTemplate.update("DELETE FROM sys_dict WHERE tenant_id = ? AND dict_code IN (?, ?)", "tenant-a", VISIBLE_DICT_CODE, HIDDEN_DICT_CODE);
        jdbcTemplate.update(
                "INSERT INTO sys_dict(tenant_id, dict_type, dict_code, dict_value, created_by, updated_by, deleted, created_at, updated_at) VALUES(?,?,?,?,?,?,0,NOW(),NOW())",
                "tenant-a", "system_scope", VISIBLE_DICT_CODE, "可见字典", VISIBLE_USER, VISIBLE_USER
        );
        jdbcTemplate.update(
                "INSERT INTO sys_dict(tenant_id, dict_type, dict_code, dict_value, created_by, updated_by, deleted, created_at, updated_at) VALUES(?,?,?,?,?,?,0,NOW(),NOW())",
                "tenant-a", "system_scope", HIDDEN_DICT_CODE, "隐藏字典", HIDDEN_USER, HIDDEN_USER
        );
        hiddenDictId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dict WHERE tenant_id = ? AND dict_code = ?",
                Long.class,
                "tenant-a",
                HIDDEN_DICT_CODE
        );
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_dict WHERE tenant_id = ? AND dict_code IN (?, ?)", "tenant-a", VISIBLE_DICT_CODE, HIDDEN_DICT_CODE);
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?, ?)", "tenant-a", SCOPE_USER, VISIBLE_USER, HIDDEN_USER);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
    }

    @Test
    void dictListShouldApplyDataScope() throws Exception {
        mockMvc.perform(get("/api/system/dicts")
                        .with(user(principal(Set.of("system:read"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.dictCode=='" + VISIBLE_DICT_CODE + "')]").exists())
                .andExpect(jsonPath("$.data[?(@.dictCode=='" + HIDDEN_DICT_CODE + "')]").doesNotExist());
    }

    @Test
    void updateHiddenDictShouldBeRejected() throws Exception {
        mockMvc.perform(put("/api/system/dicts/{id}", hiddenDictId)
                        .with(user(principal(Set.of("system:write"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictType": "system_scope",
                                  "dictCode": "SYSTEM_HIDDEN_DICT_UT",
                                  "dictValue": "越权修改"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("无权访问该字典项"));
    }

    private UserAccount principal(Set<String> permissions) {
        return new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordEncoder.encode("SystemUser@123"),
                true,
                Set.of(),
                permissions,
                Set.of(),
                DataScopeType.DEPT_AND_CHILDREN,
                1
        );
    }

    private Long ensureDept() {
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
        SysDeptEntity entity = new SysDeptEntity();
        entity.setTenantId("tenant-a");
        entity.setDeptCode(CHILD_DEPT_CODE);
        entity.setDeptName("系统管理数据权限子部门");
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
        entity.setPasswordHash(passwordEncoder.encode("SystemUser@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }
}
