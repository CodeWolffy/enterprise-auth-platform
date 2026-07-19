package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import java.time.Duration;

/** Centralizes shadow-session Redis key names and expiry policy. */
final class SessionIndexKeySpace {

    private static final String ALL_SESSIONS_KEY = "session:index:all";
    private static final String TENANT_SESSIONS_PREFIX = "session:index:tenant:";
    private static final String USER_SESSIONS_PREFIX = "session:index:user:";
    private static final String SESSION_META_PREFIX = "session:meta:";
    private static final String MANAGEMENT_TOKEN_PREFIX = "session:management:";

    private final SecurityProperties securityProperties;

    SessionIndexKeySpace(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    Duration indexTtl() {
        Duration sessionTtl = securityProperties.sessionTtl();
        if (sessionTtl == null || sessionTtl.isNegative() || sessionTtl.isZero()) {
            return Duration.ofDays(7);
        }
        return sessionTtl.plusHours(1);
    }

    String allSessionsKey() {
        return prefix() + ALL_SESSIONS_KEY;
    }

    String userSessionsKey(Long userId) {
        return userSessionsKey(String.valueOf(userId));
    }

    String userSessionsKey(String userId) {
        return prefix() + USER_SESSIONS_PREFIX + userId;
    }

    String tenantSessionsKey(String tenantId) {
        return prefix() + TENANT_SESSIONS_PREFIX + tenantId.trim();
    }

    String sessionMetaKey(String token) {
        return prefix() + SESSION_META_PREFIX + token;
    }

    String managementTokenKey(String managementId) {
        return prefix() + MANAGEMENT_TOKEN_PREFIX + managementId;
    }

    String userZsetPrefix() {
        return prefix() + USER_SESSIONS_PREFIX;
    }

    String tenantZsetPrefix() {
        return prefix() + TENANT_SESSIONS_PREFIX;
    }

    private String prefix() {
        return securityProperties.resolvedRedis().resolvedNamespacePrefix();
    }
}
