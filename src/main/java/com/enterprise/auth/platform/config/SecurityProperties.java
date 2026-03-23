package com.enterprise.auth.platform.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration captchaTtl,
        boolean exposeCaptchaAnswer,
        boolean cookieSecure,
        String cookieSameSite
) {
}
