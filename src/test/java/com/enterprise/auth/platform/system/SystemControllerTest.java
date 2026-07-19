package com.enterprise.auth.platform.system;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.enterprise.auth.platform.common.authz.DataScopeType;
import com.enterprise.auth.platform.modules.dept.infrastructure.entity.SysDeptEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.dept.infrastructure.mapper.SysDeptMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.system.interfaces.CategoryController;
import com.enterprise.auth.platform.modules.system.interfaces.ConfigController;
import com.enterprise.auth.platform.modules.system.interfaces.DictController;
import com.enterprise.auth.platform.modules.system.interfaces.NoticeController;
import com.enterprise.auth.platform.modules.system.interfaces.SystemFeatureController;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@org.junit.jupiter.api.Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerTest {

    private static final String SCOPE_USER = "system_scope_user_ut";
    private static final String VISIBLE_USER = "system_visible_user_ut";
    private static final String HIDDEN_USER = "system_hidden_user_ut";
    private static final String CHILD_DEPT_CODE = "SYSTEM_SCOPE_CHILD_UT";
    private static final String VISIBLE_DICT_CODE = "system_scope_visible_ut";
    private static final String HIDDEN_DICT_CODE = "system_scope_hidden_ut";
    private static final String ALPHA_DICT_CODE = "system_scope_alpha_ut";
    private static final String OMEGA_DICT_CODE = "system_scope_omega_ut";
    private static final String CATEGORY_CODE = "system-test-ut";
    private static final String VISIBLE_DICT_VALUE = "system-visible-value-ut";
    private static final String CREATED_DICT_VALUE = "system-created-value-ut";
    private static final String UPDATED_DICT_VALUE = "system-updated-value-ut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysDeptMapper sysDeptMapper;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private Long scopeUserId;
    private Long hiddenDictId;

    @Test
    void splitControllersShouldKeepEverySystemRouteUnique() {
        Map<String, Class<?>> actualRoutes = new LinkedHashMap<>();
        Set<Class<?>> splitControllers = Set.of(
                SystemFeatureController.class,
                CategoryController.class,
                DictController.class,
                ConfigController.class,
                NoticeController.class
        );

        requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            if (!splitControllers.contains(handlerMethod.getBeanType())) {
                return;
            }
            for (RequestMethod method : mapping.getMethodsCondition().getMethods()) {
                for (String path : mapping.getPatternValues()) {
                    String route = method.name() + " " + path;
                    assertNull(actualRoutes.put(route, handlerMethod.getBeanType()), () -> "重复路由: " + route);
                }
            }
        });

        assertEquals(expectedSplitRoutes(), actualRoutes);
    }

    @BeforeEach
    void setUp() {
        clearCaches();
        Long childDeptId = ensureDept();
        scopeUserId = ensureUser(SCOPE_USER, 2L);
        ensureSystemScopeRole(scopeUserId);
        ensureUser(VISIBLE_USER, childDeptId);
        ensureUser(HIDDEN_USER, 3L);

        jdbcTemplate.update(
                "DELETE FROM sys_dict_value WHERE tenant_id = ? AND dict_value IN (?, ?, ?)",
                "tenant-a",
                VISIBLE_DICT_VALUE,
                CREATED_DICT_VALUE,
                UPDATED_DICT_VALUE
        );
        jdbcTemplate.update(
                "DELETE FROM sys_dict WHERE tenant_id = ? AND dict_type IN (?, ?, ?, ?)",
                "tenant-a",
                VISIBLE_DICT_CODE,
                HIDDEN_DICT_CODE,
                ALPHA_DICT_CODE,
                OMEGA_DICT_CODE
        );
        insertDictType(VISIBLE_DICT_CODE, "可见字典", VISIBLE_USER);
        insertDictType(HIDDEN_DICT_CODE, "隐藏字典", HIDDEN_USER);
        insertDictType(ALPHA_DICT_CODE, "排序字典 A", VISIBLE_USER);
        insertDictType(OMEGA_DICT_CODE, "排序字典 Z", VISIBLE_USER);
        hiddenDictId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dict WHERE tenant_id = ? AND dict_code = ?",
                Long.class,
                "tenant-a",
                HIDDEN_DICT_CODE
        );
        Long visibleDictId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dict WHERE tenant_id = ? AND dict_code = ?",
                Long.class,
                "tenant-a",
                VISIBLE_DICT_CODE
        );
        jdbcTemplate.update(
                "INSERT INTO sys_dict_value(tenant_id, dict_id, dict_type, dict_label, dict_value, show_class, sort, enabled, created_by, updated_by, deleted, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,0,NOW(),NOW())",
                "tenant-a", visibleDictId, VISIBLE_DICT_CODE, "可见字典值", VISIBLE_DICT_VALUE, "success", 3, 1, VISIBLE_USER, VISIBLE_USER
        );
        jdbcTemplate.update("DELETE FROM sys_category_rule WHERE tenant_id = ? AND target_type = ? AND category_code = ?", "tenant-a", "dict", CATEGORY_CODE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update(
                "DELETE FROM sys_dict_value WHERE tenant_id = ? AND dict_value IN (?, ?, ?)",
                "tenant-a",
                VISIBLE_DICT_VALUE,
                CREATED_DICT_VALUE,
                UPDATED_DICT_VALUE
        );
        jdbcTemplate.update(
                "DELETE FROM sys_dict WHERE tenant_id = ? AND dict_type IN (?, ?, ?, ?)",
                "tenant-a",
                VISIBLE_DICT_CODE,
                HIDDEN_DICT_CODE,
                ALPHA_DICT_CODE,
                OMEGA_DICT_CODE
        );
        jdbcTemplate.update("DELETE ur FROM sys_user_role ur JOIN sys_user u ON ur.user_id = u.id WHERE ur.tenant_id = ? AND u.username IN (?, ?, ?)", "tenant-a", SCOPE_USER, VISIBLE_USER, HIDDEN_USER);
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", "tenant-a", "SYSTEM_SCOPE_ROLE_UT");
        jdbcTemplate.update("DELETE FROM sys_user WHERE tenant_id = ? AND username IN (?, ?, ?)", "tenant-a", SCOPE_USER, VISIBLE_USER, HIDDEN_USER);
        jdbcTemplate.update("DELETE FROM sys_dept WHERE tenant_id = ? AND dept_code = ?", "tenant-a", CHILD_DEPT_CODE);
        jdbcTemplate.update("DELETE FROM sys_category_rule WHERE tenant_id = ? AND target_type = ? AND category_code = ?", "tenant-a", "dict", CATEGORY_CODE);
        clearCaches();
    }

    @Test
    void reservedComponentStatusShouldReturnDisabledFlags() throws Exception {
        mockMvc.perform(get("/api/system/features")
                        .with(bearer(principal(Set.of("upms:system:get"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gatewayEnabled").value(false))
                .andExpect(jsonPath("$.data.nacosEnabled").value(false))
                .andExpect(jsonPath("$.data.mqEnabled").value(false))
                .andExpect(jsonPath("$.data.seataEnabled").value(false))
                .andExpect(jsonPath("$.data.jobEnabled").value(false))
                .andExpect(jsonPath("$.data.lokiEnabled").value(false));
    }

    @Test
    void dictListShouldApplyDataScope() throws Exception {
        mockMvc.perform(get("/api/system/dicts")
                        .with(bearer(principal(Set.of("upms:sysdict:page"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.dictType=='" + VISIBLE_DICT_CODE + "')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.dictType=='" + HIDDEN_DICT_CODE + "')]").doesNotExist());
    }

    @Test
    void dictListShouldSupportSortByDictType() throws Exception {
        mockMvc.perform(get("/api/system/dicts")
                        .with(bearer(principal(Set.of("upms:sysdict:page"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .param("sortBy", "dictType")
                        .param("sortDirection", "asc")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].dictType").value(ALPHA_DICT_CODE));
    }

    @Test
    void dictListShouldSupportCategoryFilter() throws Exception {
        mockMvc.perform(get("/api/system/dicts")
                        .with(bearer(principal(Set.of("upms:sysdict:page"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .param("category", "system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.dictType=='" + VISIBLE_DICT_CODE + "')]").exists())
                .andExpect(jsonPath("$.data.records[0].category").value("system"));
    }

    @Test
    void updateHiddenDictShouldBeRejected() throws Exception {
        mockMvc.perform(put("/api/system/dicts/{id}", hiddenDictId)
                        .with(bearer(principal(Set.of("upms:sysdict:edit"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictType": "system_scope_hidden_ut",
                                  "description": "越权修改"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("无权访问该字典项"));
    }

    @Test
    void dictDetailShouldReturnTypeAndValues() throws Exception {
        Long visibleDictId = visibleDictId();

        mockMvc.perform(get("/api/system/dicts/{id}", visibleDictId)
                        .with(bearer(principal(Set.of("upms:sysdict:get"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dict.dictType").value(VISIBLE_DICT_CODE))
                .andExpect(jsonPath("$.data.dict.valueCount").value(1))
                .andExpect(jsonPath("$.data.values[0].dictLabel").value("可见字典值"))
                .andExpect(jsonPath("$.data.values[0].dictValue").value(VISIBLE_DICT_VALUE))
                .andExpect(jsonPath("$.data.values[0].showClass").value("success"));
    }

    @Test
    void shouldManageDictValuesByDictTypeDetail() throws Exception {
        Long visibleDictId = visibleDictId();

        mockMvc.perform(post("/api/system/dicts/{id}/values", visibleDictId)
                        .with(bearer(principal(Set.of("upms:sysdict:add"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictLabel": "新字典值",
                                  "dictValue": "system-created-value-ut",
                                  "sort": 7,
                                  "showClass": "warning",
                                  "enabled": true,
                                  "remarks": "接口测试创建"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictId").value(visibleDictId))
                .andExpect(jsonPath("$.data.dictType").value(VISIBLE_DICT_CODE))
                .andExpect(jsonPath("$.data.dictLabel").value("新字典值"))
                .andExpect(jsonPath("$.data.showClass").value("warning"));

        Long valueId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dict_value WHERE tenant_id = ? AND dict_value = ?",
                Long.class,
                "tenant-a",
                CREATED_DICT_VALUE
        );

        mockMvc.perform(put("/api/system/dict-values/{valueId}", valueId)
                        .with(bearer(principal(Set.of("upms:sysdict:edit"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "dictLabel": "已更新字典值",
                                  "dictValue": "system-updated-value-ut",
                                  "sort": 8,
                                  "showClass": "danger",
                                  "enabled": false,
                                  "remarks": "接口测试更新"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dictLabel").value("已更新字典值"))
                .andExpect(jsonPath("$.data.dictValue").value(UPDATED_DICT_VALUE))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(delete("/api/system/dict-values/{valueId}", valueId)
                        .with(bearer(principal(Set.of("upms:sysdict:del"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/system/dicts/{id}", visibleDictId)
                        .with(bearer(principal(Set.of("upms:sysdict:get"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.values[?(@.dictValue=='" + UPDATED_DICT_VALUE + "')]").doesNotExist());
    }

    @Test
    void shouldCreateCategoryOption() throws Exception {
        mockMvc.perform(post("/api/system/categories/dict")
                        .with(bearer(principal(Set.of("upms:syscategory:add"))))
                        .header("X-Tenant-Id", "tenant-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "system-test-ut",
                                  "name": "系统测试分类",
                                  "matchers": ["system_scope*", "system.test.*"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(CATEGORY_CODE))
                .andExpect(jsonPath("$.data.matchers[0]").value("system_scope*"));

        mockMvc.perform(get("/api/system/categories/dict")
                        .with(bearer(principal(Set.of("upms:syscategory:get"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='" + CATEGORY_CODE + "')]").exists());

        mockMvc.perform(get("/api/system/categories/dict/{code}/analysis", CATEGORY_CODE)
                        .with(bearer(principal(Set.of("upms:syscategory:get"))))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(CATEGORY_CODE))
                .andExpect(jsonPath("$.data.referenceCount").isNumber())
                .andExpect(jsonPath("$.data.sampleReferences").isArray())
                .andExpect(jsonPath("$.data.recentAudits").isArray())
                .andExpect(jsonPath("$.data.trend").isArray());
    }

    private UserAccount principal(Set<String> permissions) {
        return new UserAccount(
                scopeUserId,
                "tenant-a",
                SCOPE_USER,
                passwordHasher.hash("SystemUser@123"),
                true,
                Set.of(),
                permissions,
                Set.of(),
                DataScopeType.DEPT_AND_CHILDREN,
                1
        );
    }

    private Map<String, Class<?>> expectedSplitRoutes() {
        Map<String, Class<?>> routes = new LinkedHashMap<>();
        routes.put("GET /api/system/features", SystemFeatureController.class);

        routes.put("GET /api/system/categories", CategoryController.class);
        routes.put("GET /api/system/categories/{targetType}", CategoryController.class);
        routes.put("GET /api/system/categories/{targetType}/{code}/analysis", CategoryController.class);
        routes.put("POST /api/system/categories/{targetType}", CategoryController.class);
        routes.put("PUT /api/system/categories/{targetType}/{code}", CategoryController.class);
        routes.put("DELETE /api/system/categories/{targetType}/{code}", CategoryController.class);

        routes.put("GET /api/system/dicts", DictController.class);
        routes.put("GET /api/system/dicts/{id}", DictController.class);
        routes.put("POST /api/system/dicts", DictController.class);
        routes.put("PUT /api/system/dicts/{id}", DictController.class);
        routes.put("DELETE /api/system/dicts/{id}", DictController.class);
        routes.put("GET /api/system/dicts/values", DictController.class);
        routes.put("GET /api/system/dicts/{id}/values", DictController.class);
        routes.put("POST /api/system/dicts/{id}/values", DictController.class);
        routes.put("GET /api/system/dict-values/{valueId}", DictController.class);
        routes.put("PUT /api/system/dict-values/{valueId}", DictController.class);
        routes.put("DELETE /api/system/dict-values/{valueId}", DictController.class);
        routes.put("DELETE /api/system/dicts/cache", DictController.class);

        routes.put("GET /api/system/configs/page", ConfigController.class);
        routes.put("GET /api/system/configs/{id}", ConfigController.class);
        routes.put("POST /api/system/configs", ConfigController.class);
        routes.put("PUT /api/system/configs/{id}", ConfigController.class);
        routes.put("DELETE /api/system/configs/{id}", ConfigController.class);
        routes.put("DELETE /api/system/configs", ConfigController.class);
        routes.put("DELETE /api/system/configs/cache", ConfigController.class);

        routes.put("GET /api/system/notices", NoticeController.class);
        routes.put("GET /api/system/notices/{id}", NoticeController.class);
        routes.put("GET /api/system/notices/{id}/published", NoticeController.class);
        routes.put("POST /api/system/notices", NoticeController.class);
        routes.put("PUT /api/system/notices/{id}", NoticeController.class);
        routes.put("DELETE /api/system/notices/{id}", NoticeController.class);
        return routes;
    }

    private Long visibleDictId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_dict WHERE tenant_id = ? AND dict_code = ?",
                Long.class,
                "tenant-a",
                VISIBLE_DICT_CODE
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
        entity.setPasswordHash(passwordHasher.hash("SystemUser@123"));
        entity.setEnabled(1);
        entity.setSessionVersion(1);
        sysUserMapper.insert(entity);
        return entity.getId();
    }

    private void insertDictType(String dictType, String description, String operator) {
        jdbcTemplate.update(
                "INSERT INTO sys_dict(tenant_id, dict_type, dict_code, dict_value, description, enabled, created_by, updated_by, deleted, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,0,NOW(),NOW())",
                "tenant-a", dictType, dictType, description, description, 1, operator, operator
        );
    }

    private void ensureSystemScopeRole(Long userId) {
        jdbcTemplate.update("DELETE FROM sys_role WHERE tenant_id = ? AND role_code = ?", "tenant-a", "SYSTEM_SCOPE_ROLE_UT");
        jdbcTemplate.update(
                "INSERT INTO sys_role(tenant_id, role_code, role_name, data_scope_type, deleted, created_at, updated_at) VALUES(?,?,?,?,0,NOW(),NOW())",
                "tenant-a", "SYSTEM_SCOPE_ROLE_UT", "系统管理测试角色", "DEPT_AND_CHILDREN"
        );
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = ?",
                Long.class,
                "tenant-a",
                "SYSTEM_SCOPE_ROLE_UT"
        );
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE tenant_id = ? AND user_id = ?", "tenant-a", userId);
        jdbcTemplate.update(
                "INSERT INTO sys_user_role(tenant_id, user_id, role_id, created_at, updated_at) VALUES(?,?,?,?,?)",
                "tenant-a", userId, roleId,
                java.sql.Timestamp.from(java.time.Instant.now()),
                java.sql.Timestamp.from(java.time.Instant.now())
        );
    }

    private void clearCaches() {
        for (String cacheName : java.util.List.of("auth:principal", "system:dicts", "system:categories:all", "system:categories:target")) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
