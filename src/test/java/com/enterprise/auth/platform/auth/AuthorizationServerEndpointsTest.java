package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
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

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @BeforeEach
    void prepareOauthClient() {
        SysOauthClientEntity existing = sysOauthClientMapper.selectIncludingDeleted("platform", "eap-web");
        SysOauthClientEntity entity = existing == null ? new SysOauthClientEntity() : existing;
        entity.setTenantId("platform");
        entity.setClientId("eap-web");
        entity.setClientSecret(passwordEncoder.encode("eap-web-secret"));
        entity.setClientName("Enterprise Auth Platform Console");
        entity.setRedirectUris("http://127.0.0.1:8080/swagger-ui/oauth2-redirect.html");
        entity.setScopes("openid,profile,api.read,api.write");
        entity.setGrantTypes("authorization_code,refresh_token,client_credentials");
        entity.setRequirePkce(0);
        entity.setRequireConsent(1);
        entity.setDeleted(0);
        if (existing == null) {
            sysOauthClientMapper.insert(entity);
        } else {
            sysOauthClientMapper.updateById(entity);
        }
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
    void shouldExposeTenantAwareLoginPageWithCsrfField() throws Exception {
        mockMvc.perform(get("/login").param("tenantId", "tenant-a").param("client_id", "eap-web"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                        .contains("default-src 'self'"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer"))
                .andExpect(content().string(Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(Matchers.containsString("name=\"tenantId\"")))
                .andExpect(content().string(Matchers.containsString("tenant-a")))
                .andExpect(content().string(Matchers.containsString("eap-web")));
    }

    @Test
    void shouldExposeConsentPageWithCsrfField() throws Exception {
        UserAccount principal = new UserAccount(
                1L,
                "platform",
                "admin",
                passwordEncoder.encode("Admin@123456"),
                true,
                Set.of("ADMIN"),
                Set.of("auth:read"),
                Set.of(),
                DataScopeType.ALL,
                1
        );
        mockMvc.perform(get("/oauth2/consent")
                        .with(user(principal))
                        .header("X-Tenant-Id", "platform")
                        .param("client_id", "eap-web")
                        .param("scope", "openid profile api.read")
                        .param("state", "state-001")
                        .param("tenantId", "platform"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(Matchers.containsString("name=\"client_id\"")))
                .andExpect(content().string(Matchers.containsString("value=\"eap-web\"")))
                .andExpect(content().string(Matchers.containsString("name=\"scope\"")));
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

    @Test
    void formLoginShouldApplyLockingPolicyForRepeatedFailures() throws Exception {
        String username = "missing-" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("tenantId", "platform")
                            .param("client_id", "eap-web")
                            .param("username", username)
                            .param("password", "wrong-password"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                            .contains("error=bad_credentials"));
        }

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("tenantId", "platform")
                        .param("client_id", "eap-web")
                        .param("username", username)
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .contains("error=locked"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("tenantId", "platform")
                        .param("client_id", "eap-web")
                        .param("username", username)
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getResponse().getRedirectedUrl())
                        .contains("error=locked"));
    }

    @Test
    void lockKeyShouldUseExpectedWindowTtl() throws Exception {
        String username = "ttl-" + UUID.randomUUID();
        String normalizedUsername = username.toLowerCase(Locale.ROOT);
        String failKey = "eap:auth:v2:login:failure:platform:" + normalizedUsername;
        String lockKey = "eap:auth:v2:login:lock:platform:" + normalizedUsername;
        stringRedisTemplate.delete(failKey);
        stringRedisTemplate.delete(lockKey);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/login")
                            .with(csrf())
                            .param("tenantId", "platform")
                            .param("client_id", "eap-web")
                            .param("username", username)
                            .param("password", "wrong-password"))
                    .andExpect(status().is3xxRedirection());
        }

        Long lockTtlSeconds = stringRedisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
        assertThat(lockTtlSeconds).isNotNull();
        assertThat(lockTtlSeconds).isGreaterThan(0L);
        assertThat(lockTtlSeconds).isLessThanOrEqualTo(Duration.ofMinutes(15).toSeconds());
        assertThat(stringRedisTemplate.hasKey(failKey)).isFalse();

        stringRedisTemplate.delete(lockKey);
    }
}
