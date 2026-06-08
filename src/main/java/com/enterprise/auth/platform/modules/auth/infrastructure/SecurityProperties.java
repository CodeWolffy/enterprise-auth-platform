package com.enterprise.auth.platform.modules.auth.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        Duration sessionTtl,
        Duration captchaTtl,
        boolean cookieSecure,
        String cookieSameSite,
        PasswordReset passwordReset,
        Notification notification,
        Redis redis
) {

    @ConstructorBinding
    public SecurityProperties {
    }

    public SecurityProperties(
            Duration sessionTtl,
            Duration captchaTtl,
            boolean cookieSecure,
            String cookieSameSite,
            Redis redis
    ) {
        this(sessionTtl, captchaTtl, cookieSecure, cookieSameSite, null, null, redis);
    }

    public PasswordReset resolvedPasswordReset() {
        return passwordReset == null ? PasswordReset.defaults() : passwordReset.withDefaults();
    }

    public Notification resolvedNotification() {
        return notification == null ? Notification.defaults() : notification.withDefaults();
    }

    public Redis resolvedRedis() {
        return redis == null ? Redis.defaults() : redis;
    }

    public record PasswordReset(
            Duration tokenTtl,
            String frontendUrl,
            long usernameWindowMinutes,
            int usernameMaxRequests,
            long ipWindowMinutes,
            int ipMaxRequests
    ) {
        private static PasswordReset defaults() {
            return new PasswordReset(Duration.ofMinutes(10), "", 15, 3, 15, 10);
        }

        private PasswordReset withDefaults() {
            PasswordReset defaults = defaults();
            return new PasswordReset(
                    tokenTtl == null ? defaults.tokenTtl() : tokenTtl,
                    frontendUrl == null || frontendUrl.isBlank() ? defaults.frontendUrl() : frontendUrl,
                    usernameWindowMinutes <= 0 ? defaults.usernameWindowMinutes() : usernameWindowMinutes,
                    usernameMaxRequests <= 0 ? defaults.usernameMaxRequests() : usernameMaxRequests,
                    ipWindowMinutes <= 0 ? defaults.ipWindowMinutes() : ipWindowMinutes,
                    ipMaxRequests <= 0 ? defaults.ipMaxRequests() : ipMaxRequests
            );
        }
    }

    public record Notification(
            String channel,
            String mailFrom
    ) {
        private static Notification defaults() {
            return new Notification("log", "noreply@enterprise-auth-platform.local");
        }

        private Notification withDefaults() {
            Notification defaults = defaults();
            return new Notification(
                    channel == null || channel.isBlank() ? defaults.channel() : channel.trim().toLowerCase(),
                    mailFrom == null || mailFrom.isBlank() ? defaults.mailFrom() : mailFrom.trim()
            );
        }
    }

    public record Redis(
            boolean sessionEnabled,
            boolean captchaEnabled,
            boolean redissonEnabled,
            String keyPrefix,
            String namespaceVersion
    ) {

        private static Redis defaults() {
            return new Redis(true, true, true, "eap:auth:", "v1");
        }

        public String resolvedKeyPrefix() {
            if (keyPrefix == null || keyPrefix.isBlank()) {
                return "eap:auth:";
            }
            return keyPrefix.endsWith(":") ? keyPrefix : keyPrefix + ":";
        }

        public String resolvedNamespaceVersion() {
            if (namespaceVersion == null || namespaceVersion.isBlank()) {
                return "v1";
            }
            return namespaceVersion.trim();
        }

        public String resolvedNamespacePrefix() {
            return resolvedKeyPrefix() + resolvedNamespaceVersion() + ":";
        }
    }
}