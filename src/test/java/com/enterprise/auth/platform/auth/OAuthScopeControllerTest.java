package com.enterprise.auth.platform.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
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
class OAuthScopeControllerTest {

    private static final String SCOPE_CODE = "scope.controller.ut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM sys_oauth_scope WHERE tenant_id = 'platform' AND scope_code = ?", SCOPE_CODE);
    }

    @Test
    void shouldCreateQueryUpdateAndDeleteScope() throws Exception {
        UserAccount principal = principal(Set.of("auth:read", "auth:write"));

        mockMvc.perform(post("/api/oauth-scopes")
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scopeCode": "scope.controller.ut",
                                  "scopeName": "测试作用域",
                                  "scopeDesc": "用于接口回归测试",
                                  "scopeType": "API",
                                  "defaultSelected": false,
                                  "visibleInConsent": true,
                                  "sortOrder": 66,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeCode").value(SCOPE_CODE));

        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_oauth_scope WHERE tenant_id = 'platform' AND scope_code = ? LIMIT 1",
                Long.class,
                SCOPE_CODE
        );

        mockMvc.perform(get("/api/oauth-scopes")
                        .with(user(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.scopeCode=='" + SCOPE_CODE + "')]").exists());

        mockMvc.perform(put("/api/oauth-scopes/{id}", id)
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scopeCode": "scope.controller.ut",
                                  "scopeName": "测试作用域已更新",
                                  "scopeDesc": "用于接口回归测试更新",
                                  "scopeType": "API",
                                  "defaultSelected": true,
                                  "visibleInConsent": true,
                                  "sortOrder": 88,
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeName").value("测试作用域已更新"))
                .andExpect(jsonPath("$.data.defaultSelected").value(true));

        mockMvc.perform(delete("/api/oauth-scopes/{id}", id)
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private UserAccount principal(Set<String> permissions) {
        return new UserAccount(
                1L,
                "platform",
                "admin",
                passwordEncoder.encode("Admin@123456"),
                true,
                Set.of("ADMIN"),
                permissions,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
