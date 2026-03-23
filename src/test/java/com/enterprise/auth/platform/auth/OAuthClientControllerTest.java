package com.enterprise.auth.platform.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OAuthClientControllerTest {

    private static final String CLIENT_ID = "oauth_client_controller_ut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysOauthClientMapper sysOauthClientMapper;

    @AfterEach
    void tearDown() {
        sysOauthClientMapper.hardDeleteByTenantAndClientId("platform", CLIENT_ID);
        sysOauthClientMapper.hardDeleteByTenantAndClientId("tenant-b", CLIENT_ID);
    }

    @Test
    void shouldCreateQueryUpdateRotateToggleAndDeleteClient() throws Exception {
        UserAccount principal = principal(Set.of("auth:read", "auth:write"));

        mockMvc.perform(post("/api/oauth-clients")
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId": "oauth_client_controller_ut",
                                  "clientSecret": "ClientSecret@123",
                                  "clientName": "接口联调客户端",
                                  "redirectUris": ["http://127.0.0.1:8081/callback"],
                                  "scopes": ["openid", "api.read"],
                                  "grantTypes": ["authorization_code", "refresh_token", "client_credentials"],
                                  "requirePkce": true,
                                  "requireConsent": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientId").value(CLIENT_ID))
                .andExpect(jsonPath("$.data.issuedClientSecret").value("ClientSecret@123"));

        Long clientRowId = sysOauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientEntity>()
                        .eq(SysOauthClientEntity::getTenantId, "platform")
                        .eq(SysOauthClientEntity::getClientId, CLIENT_ID)
                        .last("limit 1"))
                .getId();

        mockMvc.perform(get("/api/oauth-clients/{id}", clientRowId)
                        .with(user(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientName").value("接口联调客户端"))
                .andExpect(jsonPath("$.data.scopeDescriptions.openid").exists())
                .andExpect(jsonPath("$.data.scopeDetails").isArray())
                .andExpect(jsonPath("$.data.scopeTypeSummary").isMap())
                .andExpect(jsonPath("$.data.integrationGuidance.recommendedGrantType").exists())
                .andExpect(jsonPath("$.data.statusHistory").isArray());

        mockMvc.perform(put("/api/oauth-clients/{id}", clientRowId)
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientName": "接口联调客户端-更新",
                                  "clientSecret": "ClientSecret@456",
                                  "redirectUris": ["http://127.0.0.1:8081/callback", "http://127.0.0.1:8081/silent"],
                                  "scopes": ["openid", "api.read", "api.write"],
                                  "grantTypes": ["authorization_code", "refresh_token"],
                                  "requirePkce": false,
                                  "requireConsent": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clientName").value("接口联调客户端-更新"))
                .andExpect(jsonPath("$.data.issuedClientSecret").value("ClientSecret@456"));

        mockMvc.perform(post("/api/oauth-clients/{id}/rotate-secret", clientRowId)
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientSecret": "ClientSecret@789"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.issuedClientSecret").value("ClientSecret@789"));

        mockMvc.perform(put("/api/oauth-clients/{id}/status", clientRowId)
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(delete("/api/oauth-clients/{id}", clientRowId)
                        .with(user(principal))
                        .with(csrf())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReturnAccessDeniedWhenAuthorityMissing() throws Exception {
        mockMvc.perform(get("/api/oauth-clients")
                        .with(user(principal(Set.of())))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("无权访问当前资源"));
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
