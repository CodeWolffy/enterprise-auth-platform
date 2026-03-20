package com.enterprise.auth.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.authorization-server")
public record AuthorizationServerProperties(String issuer) {
}
