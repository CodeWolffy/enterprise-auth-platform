package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.sql.PreparedStatement;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OauthConsentIsolationTest {

    private static final String PLATFORM_CLIENT_ID = "consent-platform-ut";
    private static final String TENANT_CLIENT_ID = "consent-tenant-ut";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String platformRegisteredClientId;
    private String tenantRegisteredClientId;

    @BeforeEach
    void setUp() {
        cleanup();

        platformRegisteredClientId = String.valueOf(insertClient("platform", PLATFORM_CLIENT_ID));
        tenantRegisteredClientId = String.valueOf(insertClient("tenant-a", TENANT_CLIENT_ID));

        jdbcTemplate.update(
                "INSERT INTO oauth2_authorization_consent(registered_client_id, principal_name, authorities) VALUES (?, ?, ?)",
                platformRegisteredClientId,
                "platform-user",
                "SCOPE_openid,SCOPE_api.read"
        );
        jdbcTemplate.update(
                "INSERT INTO oauth2_authorization_consent(registered_client_id, principal_name, authorities) VALUES (?, ?, ?)",
                tenantRegisteredClientId,
                "tenant-user",
                "SCOPE_openid,SCOPE_api.read"
        );
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    @Test
    void queryShouldOnlyReturnCurrentTenantConsents() throws Exception {
        mockMvc.perform(get("/api/auth/consents")
                        .with(user(principal(Set.of("auth:read"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].tenantId").value("platform"))
                .andExpect(jsonPath("$.data.records[0].clientId").value(PLATFORM_CLIENT_ID));
    }

    @Test
    void revokeShouldRejectCrossTenantConsent() throws Exception {
        mockMvc.perform(delete("/api/auth/consents")
                        .with(user(principal(Set.of("auth:write"))))
                        .header("X-Tenant-Id", "platform")
                        .param("registeredClientId", tenantRegisteredClientId)
                        .param("principalName", "tenant-user"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization_consent WHERE registered_client_id = ? AND principal_name = ?",
                Long.class,
                tenantRegisteredClientId,
                "tenant-user"
        );
        assertThat(count).isEqualTo(1L);
    }

    private long insertClient(String tenantId, String clientId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO sys_oauth_client(
                        tenant_id, client_id, client_secret, client_name, redirect_uris,
                        scopes, grant_types, require_pkce, require_consent, client_status,
                        created_by, updated_by, deleted
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                    """,
                    PreparedStatement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, tenantId);
            ps.setString(2, clientId);
            ps.setString(3, passwordEncoder.encode("ConsentClient@123"));
            ps.setString(4, "Consent UT " + tenantId);
            ps.setString(5, "http://127.0.0.1:5173/auth/callback");
            ps.setString(6, "openid,api.read");
            ps.setString(7, "authorization_code,refresh_token");
            ps.setInt(8, 1);
            ps.setInt(9, 1);
            ps.setInt(10, 1);
            ps.setString(11, "test");
            ps.setString(12, "test");
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to insert oauth client for test");
        }
        return key.longValue();
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

    private void cleanup() {
        jdbcTemplate.update(
                "DELETE FROM oauth2_authorization_consent WHERE principal_name IN (?, ?)",
                "platform-user",
                "tenant-user"
        );
        jdbcTemplate.update("DELETE FROM sys_oauth_client WHERE client_id IN (?, ?)", PLATFORM_CLIENT_ID, TENANT_CLIENT_ID);
    }
}
