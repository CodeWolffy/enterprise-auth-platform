package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.security.AuthPrincipalCacheService;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationCodeSessionFlowTest {

    private static final String CLIENT_ID = "eap-session-flow-test";
    private static final String CLIENT_SECRET = "SessionFlowSecret@123";
    private static final String REDIRECT_URI = "http://127.0.0.1:5173/auth/callback";
        private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysOauthClientMapper sysOauthClientMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthPrincipalCacheService authPrincipalCacheService;

    private Integer originalSessionVersion;

    @BeforeEach
    void setUp() {
        sysOauthClientMapper.hardDeleteByTenantAndClientId("platform", CLIENT_ID);
        SysOauthClientEntity client = new SysOauthClientEntity();
        client.setTenantId("platform");
        client.setClientId(CLIENT_ID);
        client.setClientSecret(passwordEncoder.encode(CLIENT_SECRET));
        client.setClientName("Authorization Code Session Flow Test");
        client.setRedirectUris(REDIRECT_URI);
        client.setScopes("openid,profile,api.read");
        client.setGrantTypes("authorization_code,refresh_token");
        client.setRequirePkce(0);
        client.setRequireConsent(0);
        client.setClientStatus(1);
        client.setDeleted(0);
        client.setCreatedBy("test");
        client.setUpdatedBy("test");
        sysOauthClientMapper.insert(client);

        SysUserEntity admin = loadAdminEntity();
        originalSessionVersion = admin.getSessionVersion();
        authPrincipalCacheService.evictByUser(admin.getId(), admin.getTenantId(), admin.getUsername());
    }

    @AfterEach
    void tearDown() {
        sysOauthClientMapper.hardDeleteByTenantAndClientId("platform", CLIENT_ID);
        if (originalSessionVersion != null) {
            SysUserEntity admin = loadAdminEntity();
            admin.setSessionVersion(originalSessionVersion);
            admin.setEnabled(1);
            admin.setUpdatedBy("test");
            sysUserMapper.updateById(admin);
            authPrincipalCacheService.evictByUser(admin.getId(), admin.getTenantId(), admin.getUsername());
        }
    }

    @Test
    void logoutShouldRevokeAuthorizationServerAccessAndRefreshTokens() throws Exception {
        OAuthTokens tokens = issueTokens();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));

        mockMvc.perform(post("/api/auth/logout")
                        .with(csrf())
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", CLIENT_SECRET)
                        .param("refresh_token", tokens.refreshToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void forceOfflineShouldRevokeAuthorizationServerAccessAndRefreshTokens() throws Exception {
        OAuthTokens tokens = issueTokens();

        MvcResult sessions = mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").isNotEmpty())
                .andReturn();

        String sessionId = objectMapper.readTree(sessions.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path(0)
                .path("sessionId")
                .asText();

        mockMvc.perform(post("/api/auth/sessions/{sessionId}/offline", sessionId)
                        .with(csrf())
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", CLIENT_SECRET)
                        .param("refresh_token", tokens.refreshToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void sessionsEndpointShouldExposeAuthorizationServerSession() throws Exception {
        OAuthTokens tokens = issueTokens();

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("admin"))
                .andExpect(jsonPath("$.data[0].tenantId").value("platform"))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void sessionVersionChangeShouldInvalidateAuthorizationServerTokens() throws Exception {
        OAuthTokens tokens = issueTokens();
        UserAccount admin = userRepository.findByUsername("platform", "admin").orElseThrow();
        userRepository.incrementSessionVersion(admin.id());

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", CLIENT_SECRET)
                        .param("refresh_token", tokens.refreshToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void platformAdminTokenShouldAllowTenantSwitch() throws Exception {
        OAuthTokens tokens = issueTokens();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + tokens.accessToken())
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isOk());
    }

    private OAuthTokens issueTokens() throws Exception {
        UserAccount principal = userRepository.findByUsername("platform", "admin").orElseThrow();
        MvcResult authorizeResult = mockMvc.perform(get(
                        "/oauth2/authorize?response_type={responseType}&client_id={clientId}&redirect_uri={redirectUri}&scope={scope}&state={state}&tenantId={tenantId}",
                        "code",
                        CLIENT_ID,
                        REDIRECT_URI,
                        "openid profile api.read",
                        "state-001",
                        "platform"
                )
                        .with(user(principal))
                        .header("X-Tenant-Id", "platform")
                        .header("User-Agent", "MockMvc Session Flow"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectUrl = authorizeResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).isNotBlank();
        String code = UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams().getFirst("code");
        assertThat(code).isNotBlank();

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", CLIENT_ID)
                        .param("client_secret", CLIENT_SECRET)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andReturn();

        JsonNode payload = objectMapper.readTree(tokenResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return new OAuthTokens(
                payload.path("access_token").asText(),
                payload.path("refresh_token").asText()
        );
    }

    private SysUserEntity loadAdminEntity() {
        return sysUserMapper.selectOne(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getTenantId, "platform")
                .eq(SysUserEntity::getUsername, "admin")
                .eq(SysUserEntity::getDeleted, 0)
                .last("limit 1"));
    }

    private record OAuthTokens(String accessToken, String refreshToken) {
    }
}
