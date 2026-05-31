package com.enterprise.auth.platform.common.cache;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 平台缓存 key 统一构造入口。
 */
public final class CacheKeys {

    private static final String PREFIX = "eap";

    private CacheKeys() {
    }

    public static String of(String namespace, Object... parts) {
        String suffix = Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(":"));
        if (suffix.isEmpty()) {
            return PREFIX + ":" + namespace;
        }
        return PREFIX + ":" + namespace + ":" + suffix;
    }

    public static String permissionSnapshot(String tenantId, Long userId) {
        return of(CacheNamespaces.PERMISSION, "snapshot", tenantId, userId);
    }

    public static String menuTree(String tenantId) {
        return of(CacheNamespaces.MENU, "tree", tenantId);
    }

    public static String roleGrants(Long roleId) {
        return of(CacheNamespaces.ROLE, "grants", roleId);
    }

    public static String systemConfig(String configKey) {
        return of(CacheNamespaces.SYSTEM, "config", configKey);
    }

    public static String tenantCatalog(String tenantId) {
        return of(CacheNamespaces.TENANT, "catalog", tenantId);
    }

    public static String sessionMeta(String sessionId) {
        return of(CacheNamespaces.SESSION, "meta", sessionId);
    }

    public static String auditExportPolicy(String tenantId) {
        return of(CacheNamespaces.AUDIT, "export-policy", tenantId);
    }

    public static String rateLimit(String scope, String identity) {
        return of(CacheNamespaces.RATE_LIMIT, scope, identity);
    }
}