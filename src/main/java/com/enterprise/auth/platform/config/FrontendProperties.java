package com.enterprise.auth.platform.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(
        List<String> allowedOrigins,
        String publicClientId,
        String publicClientName,
        List<String> redirectUris,
        List<String> scopes
) {

    public List<String> resolvedAllowedOrigins() {
        return allowedOrigins == null ? List.of() : allowedOrigins;
    }

    public List<String> resolvedRedirectUris() {
        return redirectUris == null ? List.of() : redirectUris;
    }

    public List<String> resolvedScopes() {
        return scopes == null ? List.of() : scopes;
    }
}
