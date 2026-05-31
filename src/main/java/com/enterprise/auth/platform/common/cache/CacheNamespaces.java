package com.enterprise.auth.platform.common.cache;

/**
 * 平台缓存命名空间登记入口。
 */
public final class CacheNamespaces {

    public static final String AUTH = "auth";
    public static final String SESSION = "session";
    public static final String PERMISSION = "permission";
    public static final String MENU = "menu";
    public static final String ROLE = "role";
    public static final String SYSTEM = "system";
    public static final String TENANT = "tenant";
    public static final String AUDIT = "audit";
    public static final String RATE_LIMIT = "rate-limit";

    private CacheNamespaces() {
    }
}