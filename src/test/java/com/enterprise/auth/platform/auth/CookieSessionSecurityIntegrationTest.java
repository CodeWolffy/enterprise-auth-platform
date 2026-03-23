package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.persistence.entity.SysOauthClientEntity;
import com.enterprise.auth.platform.persistence.mapper.SysOauthClientMapper;
import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
class CookieSessionSecurityIntegrationTest {

    private static final String FRONTEND_CLIENT_ID = "eap-frontend-spa";
    private static final String REDIRECT_URI = "http://127.0.0.1:5173/auth/callback";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FrontendProperties frontendProperties;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SysOauthClientMapper sysOauthClientMapper;

    private SysOauthClientEntity originalClient;
    private boolean createdClient;

    @BeforeEach
    void prepareFrontendClientForCookieFlow() {
        SysOauthClientEntity existing = sysOauthClientMapper.selectIncludingDeleted("platform", FRONTEND_CLIENT_ID);
        if (existing == null) {
            createdClient = true;
            originalClient = null;
            SysOauthClientEntity created = new SysOauthClientEntity();
            created.setTenantId("platform");
            created.setClientId(FRONTEND_CLIENT_ID);
            created.setClientName("Frontend Console");
            created.setClientSecret(passwordEncoder.encode(frontendProperties.publicClientSecret()));
            created.setRedirectUris(REDIRECT_URI);
            created.setScopes("openid,profile,api.read");
            created.setGrantTypes("authorization_code,refresh_token");
            created.setRequirePkce(0);
            created.setRequireConsent(0);
            created.setClientStatus(1);
            created.setDeleted(0);
            created.setCreatedBy("test");
            created.setUpdatedBy("test");
            sysOauthClientMapper.insert(created);
            return;
        }

        createdClient = false;
        originalClient = copyOf(existing);
        existing.setClientSecret(passwordEncoder.encode(frontendProperties.publicClientSecret()));
        existing.setRedirectUris(REDIRECT_URI);
        existing.setScopes("openid,profile,api.read");
        existing.setGrantTypes("authorization_code,refresh_token");
        existing.setRequirePkce(0);
        existing.setRequireConsent(0);
        existing.setClientStatus(1);
        existing.setDeleted(0);
        existing.setUpdatedBy("test");
        sysOauthClientMapper.updateById(existing);
    }

    @AfterEach
    void restoreFrontendClient() {
        if (createdClient) {
            sysOauthClientMapper.hardDeleteByTenantAndClientId("platform", FRONTEND_CLIENT_ID);
            return;
        }
        if (originalClient != null) {
            sysOauthClientMapper.updateById(originalClient);
        }
    }

    @Test
    void oauthCookieRefreshShouldEnforceCsrfAndRotateCookies() throws Exception {
        OAuthTokens tokens = issueFrontendTokens();

        CsrfContext csrfContext = fetchCsrf();

        mockMvc.perform(post("/api/auth/oauth/refresh")
                        .cookie(new Cookie(AuthCookieConstants.REFRESH_TOKEN_COOKIE, tokens.refreshToken())))
                .andExpect(status().isForbidden());

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/oauth/refresh")
                        .cookie(
                                new Cookie(AuthCookieConstants.REFRESH_TOKEN_COOKIE, tokens.refreshToken()),
                                copyCookie(csrfContext.xsrfCookie())
                        )
                        .header(csrfContext.headerName(), csrfContext.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tenantId").value("platform"))
                .andExpect(jsonPath("$.data.sessionId").isNotEmpty())
                .andReturn();

        List<String> setCookieHeaders = refreshResult.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeaders).anySatisfy(value -> assertThat(value)
                .contains(AuthCookieConstants.ACCESS_TOKEN_COOKIE + "=")
                .contains("HttpOnly")
                .contains("SameSite=Lax"));
        assertThat(setCookieHeaders).anySatisfy(value -> assertThat(value)
                .contains(AuthCookieConstants.REFRESH_TOKEN_COOKIE + "=")
                .contains("HttpOnly")
                .contains("SameSite=Lax"));
    }

    @Test
    void forgedTenantHeaderShouldBeRejectedInCookieMode() throws Exception {
        OAuthTokens tokens = issueFrontendTokens();

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(AuthCookieConstants.ACCESS_TOKEN_COOKIE, tokens.accessToken()))
                        .header("X-Tenant-Id", "tenant-a"))
                .andExpect(status().isForbidden());
    }

    private OAuthTokens issueFrontendTokens() throws Exception {
        String codeVerifier = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        String codeChallenge = codeChallenge(codeVerifier);
        UserAccount principal = userRepository.findByUsername("platform", "admin").orElseThrow();
        MvcResult authorizeResult = mockMvc.perform(get(
                        "/oauth2/authorize?response_type={responseType}&client_id={clientId}&redirect_uri={redirectUri}&scope={scope}&state={state}&tenantId={tenantId}&code_challenge={codeChallenge}&code_challenge_method={challengeMethod}",
                        "code",
                        FRONTEND_CLIENT_ID,
                        REDIRECT_URI,
                        "openid profile api.read",
                        "state-cookie-001",
                        "platform",
                        codeChallenge,
                        "S256"
                )
                        .with(user(principal))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String redirectUrl = authorizeResult.getResponse().getRedirectedUrl();
        assertThat(redirectUrl).isNotBlank();
        String code = UriComponentsBuilder.fromUriString(redirectUrl).build().getQueryParams().getFirst("code");
        assertThat(code).isNotBlank();

        MvcResult tokenResult = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", FRONTEND_CLIENT_ID)
                        .param("client_secret", frontendProperties.publicClientSecret())
                        .param("redirect_uri", REDIRECT_URI)
                        .param("code", code)
                        .param("code_verifier", codeVerifier))
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

    private CsrfContext fetchCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").isNotEmpty())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        Cookie xsrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).isNotNull();
        return new CsrfContext(data.path("headerName").asText(), data.path("token").asText(), xsrfCookie);
    }

    private Cookie copyCookie(Cookie source) {
        Cookie cookie = new Cookie(source.getName(), source.getValue());
        cookie.setPath(source.getPath());
        cookie.setSecure(source.getSecure());
        cookie.setHttpOnly(source.isHttpOnly());
        return cookie;
    }

    private String codeChallenge(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private SysOauthClientEntity copyOf(SysOauthClientEntity source) {
        SysOauthClientEntity copy = new SysOauthClientEntity();
        copy.setId(source.getId());
        copy.setTenantId(source.getTenantId());
        copy.setClientId(source.getClientId());
        copy.setClientSecret(source.getClientSecret());
        copy.setClientName(source.getClientName());
        copy.setRedirectUris(source.getRedirectUris());
        copy.setScopes(source.getScopes());
        copy.setGrantTypes(source.getGrantTypes());
        copy.setRequirePkce(source.getRequirePkce());
        copy.setRequireConsent(source.getRequireConsent());
        copy.setClientStatus(source.getClientStatus());
        copy.setCreatedBy(source.getCreatedBy());
        copy.setUpdatedBy(source.getUpdatedBy());
        copy.setDeleted(source.getDeleted());
        return copy;
    }

    private record OAuthTokens(String accessToken, String refreshToken) {
    }

    private record CsrfContext(String headerName, String token, Cookie xsrfCookie) {
    }
}
