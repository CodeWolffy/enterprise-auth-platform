package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationServerEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredClientRepository registeredClientRepository;

    @Autowired
    private SysOauthClientMapper sysOauthClientMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepareOauthClient() {
        SysOauthClientEntity existing = sysOauthClientMapper.selectOne(new LambdaQueryWrapper<SysOauthClientEntity>()
                .eq(SysOauthClientEntity::getTenantId, "platform")
                .eq(SysOauthClientEntity::getClientId, "eap-web")
                .last("limit 1"));
        SysOauthClientEntity entity = existing == null ? new SysOauthClientEntity() : existing;
        entity.setTenantId("platform");
        entity.setClientId("eap-web");
        entity.setClientSecret(passwordEncoder.encode("eap-web-secret"));
        entity.setClientName("企业权限管理平台管理端");
        entity.setRedirectUris("http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html");
        entity.setScopes("openid,profile,api.read,api.write");
        entity.setGrantTypes("authorization_code,refresh_token,client_credentials");
        entity.setRequirePkce(0);
        entity.setRequireConsent(0);
        entity.setDeleted(0);
        if (existing == null) {
            sysOauthClientMapper.insert(entity);
            return;
        }
        sysOauthClientMapper.updateById(entity);
    }

    @Test
    void shouldExposeOpenIdConfiguration() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://127.0.0.1:8080"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://127.0.0.1:8080/oauth2/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://127.0.0.1:8080/oauth2/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://127.0.0.1:8080/oauth2/jwks"));
    }

    @Test
    void shouldExposeJwkSet() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").isNotEmpty());
    }

    @Test
    void shouldRegisterDatabaseClient() {
        assertThat(registeredClientRepository.findByClientId("eap-web")).isNotNull();
    }

    @Test
    void shouldExposeTenantAwareLoginPage() throws Exception {
        mockMvc.perform(get("/login").param("tenantId", "tenant-a"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("统一认证登录")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"tenantId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("tenant-a")));
    }

    @Test
    void shouldIssueTokenWithDatabaseRegisteredClient() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic("eap-web", "eap-web-secret"))
                        .contentType("application/x-www-form-urlencoded")
                        .param("grant_type", "client_credentials")
                        .param("scope", "api.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.scope").value("api.read"));
    }
}
