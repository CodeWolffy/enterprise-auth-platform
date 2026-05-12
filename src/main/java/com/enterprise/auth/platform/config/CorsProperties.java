package com.enterprise.auth.platform.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {

    public List<String> resolvedAllowedOrigins() {
        return allowedOrigins == null ? List.of() : allowedOrigins;
    }
}