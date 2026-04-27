package com.enterprise.auth.platform.auth;

import static com.enterprise.auth.platform.test.SaTokenMockMvcSupport.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.enterprise.auth.platform.common.model.DataScopeType;
import com.enterprise.auth.platform.security.PasswordHasher;
import com.enterprise.auth.platform.user.model.UserAccount;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordHasher passwordHasher;

    @Test
    void protectedApiShouldRequireLoginBeforePermissionCheck() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void userReadEndpointShouldRequireUserReadPermission() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(bearer(principal(Set.of())))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/users")
                        .with(bearer(principal(Set.of("user:read"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));
    }

    @Test
    void userWriteEndpointShouldRequireUserWriteBeforeValidation() throws Exception {
        String invalidPayload = """
                {
                  "username": "",
                  "displayName": "Invalid",
                  "password": "UserTest@123",
                  "deptId": 1,
                  "enabled": true,
                  "roleCodes": []
                }
                """;

        mockMvc.perform(post("/api/users")
                        .with(bearer(principal(Set.of("user:read"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(post("/api/users")
                        .with(bearer(principal(Set.of("user:write"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void departmentReadAndWriteShouldUseSeparatePermissionKeys() throws Exception {
        mockMvc.perform(get("/api/depts")
                        .with(bearer(principal(Set.of("dept:write"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/depts")
                        .with(bearer(principal(Set.of("dept:read"))))
                        .header("X-Tenant-Id", "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"));

        mockMvc.perform(post("/api/depts")
                        .with(bearer(principal(Set.of("dept:read"))))
                        .header("X-Tenant-Id", "platform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentId": null,
                                  "deptCode": "AUTH_BOUNDARY_DEPT",
                                  "deptName": "",
                                  "leaderUserId": null
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    private UserAccount principal(Set<String> permissions) {
        return new UserAccount(
                1L,
                "platform",
                "authorization_boundary_user",
                passwordHasher.hash("Boundary@123"),
                true,
                Set.of(),
                permissions,
                Set.of(),
                DataScopeType.ALL,
                1
        );
    }
}
