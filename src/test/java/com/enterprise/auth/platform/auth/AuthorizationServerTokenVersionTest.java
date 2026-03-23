package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.config.FrontendProperties;
import com.enterprise.auth.platform.persistence.entity.SysUserEntity;
import com.enterprise.auth.platform.persistence.mapper.SysUserMapper;
import com.enterprise.auth.platform.user.model.UserAccount;
import com.enterprise.auth.platform.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AuthorizationServerTokenVersionTest {

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
    private SysUserMapper sysUserMapper;

    @Test
    void accessTokenVerShouldMatchCurrentUserSessionVersion() throws Exception {
        UserAccount principal = userRepository.findByUsername("platform", "admin").orElseThrow();
        String accessToken = issueAccessToken(principal);
        JsonNode payload = decodeJwtPayload(accessToken);
        int ver = payload.path("ver").asInt();

        SysUserEntity admin = sysUserMapper.selectById(principal.id());
        assertThat(admin).isNotNull();
        assertThat(ver).isEqualTo(admin.getSessionVersion());
    }

    private String issueAccessToken(UserAccount principal) throws Exception {
        String state = "state-version-check";
        String codeVerifier = UUID.randomUUID() + UUID.randomUUID().toString().replace("-", "");
        String codeChallenge = codeChallenge(codeVerifier);
        MvcResult authorizeResult = mockMvc.perform(get(
                        "/oauth2/authorize?response_type={responseType}&client_id={clientId}&redirect_uri={redirectUri}&scope={scope}&state={state}&tenantId={tenantId}&code_challenge={codeChallenge}&code_challenge_method={challengeMethod}",
                        "code",
                        FRONTEND_CLIENT_ID,
                        REDIRECT_URI,
                        "openid profile api.read",
                        state,
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
                        .param("code_verifier", codeVerifier)
                        .param("tenantId", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();

        JsonNode tokenPayload = objectMapper.readTree(tokenResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return tokenPayload.path("access_token").asText();
    }

    private JsonNode decodeJwtPayload(String jwtToken) throws Exception {
        String[] parts = jwtToken.split("\\.");
        assertThat(parts).hasSize(3);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return objectMapper.readTree(payload);
    }

    private String codeChallenge(String codeVerifier) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }
}
