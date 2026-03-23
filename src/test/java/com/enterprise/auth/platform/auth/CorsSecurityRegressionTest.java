package com.enterprise.auth.platform.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CorsSecurityRegressionTest {

    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiCorsShouldAllowConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void apiCorsShouldRejectNullOrigin() throws Exception {
        mockMvc.perform(get("/api/auth/csrf")
                        .header("Origin", "null"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Invalid CORS request")));
    }

    @Test
    void loginFormShouldNotBeRejectedByApiCorsPolicy() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .header("Origin", "null")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "invalid-password")
                        .param("tenantId", "platform"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/login**"))
                .andExpect(content().string(not(containsString("Invalid CORS request"))));
    }
}
