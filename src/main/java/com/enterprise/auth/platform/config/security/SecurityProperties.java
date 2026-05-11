package com.enterprise.auth.platform.config.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Duration sessionTtl,
        Duration captchaTtl,
        boolean cookieSecure,
        String cookieSameSite
) {
}
