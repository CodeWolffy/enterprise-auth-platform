package com.enterprise.auth.platform.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CorsSecurityRegressionTest {

    private static final String ALLOWED_ORIGIN = "http://127.0.0.1:5777";
    private static final String LOCALHOST_ORIGIN = "http://localhost:5777";
    private static final String LAN_ORIGIN = "http://192.168.1.9:5777";
    private static final String VIRTUAL_NETWORK_ORIGIN = "http://198.18.0.1:5777";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiCorsShouldAllowConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(get("/api/auth/register/options")
                        .header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    @Test
    void apiCorsShouldRejectNullOrigin() throws Exception {
        mockMvc.perform(get("/api/auth/register/options")
                        .header("Origin", "null"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("Invalid CORS request")));
    }

    @Test
    void loginApiPreflightShouldAllowConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,authorization,x-tenant-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("POST")));
    }

    @Test
    void apiCorsShouldAllowViteNetworkOriginsInDevelopment() throws Exception {
        mockMvc.perform(options("/api/auth/captcha")
                        .header("Origin", LAN_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", LAN_ORIGIN));

        mockMvc.perform(options("/api/auth/captcha")
                        .header("Origin", VIRTUAL_NETWORK_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", VIRTUAL_NETWORK_ORIGIN));
    }

    @Test
    void protectedApiPreflightShouldBypassAuthentication() throws Exception {
        mockMvc.perform(options("/api/auth/me")
                        .header("Origin", LOCALHOST_ORIGIN)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization,x-tenant-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", LOCALHOST_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
    }
}
