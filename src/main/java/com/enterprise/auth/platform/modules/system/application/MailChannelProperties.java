package com.enterprise.auth.platform.modules.system.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "app.mail")
public record MailChannelProperties(
        String secretKey,
        Integer connectionTimeoutMillis,
        Integer timeoutMillis,
        Integer writeTimeoutMillis,
        Boolean debug
) {

    public String resolvedSecretKey() {
        return StringUtils.hasText(secretKey) ? secretKey.trim() : "";
    }

    public int resolvedConnectionTimeoutMillis() {
        return positiveOrDefault(connectionTimeoutMillis, 10000);
    }

    public int resolvedTimeoutMillis() {
        return positiveOrDefault(timeoutMillis, 10000);
    }

    public int resolvedWriteTimeoutMillis() {
        return positiveOrDefault(writeTimeoutMillis, 10000);
    }

    public boolean resolvedDebug() {
        return Boolean.TRUE.equals(debug);
    }

    private int positiveOrDefault(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }
}