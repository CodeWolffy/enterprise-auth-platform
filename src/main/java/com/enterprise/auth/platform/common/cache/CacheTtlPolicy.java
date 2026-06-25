package com.enterprise.auth.platform.common.cache;

import java.time.Duration;

/**
 * 平台缓存 TTL 登记入口。
 */
public final class CacheTtlPolicy {

    public static final Duration SHORT_LIVED = Duration.ofMinutes(5);
    public static final Duration PERMISSION_SNAPSHOT = Duration.ofMinutes(10);
    public static final Duration MENU_TREE = Duration.ofMinutes(10);
    public static final Duration ROLE_GRANTS = Duration.ofMinutes(10);
    public static final Duration SYSTEM_CONFIG = Duration.ofMinutes(30);
    public static final Duration TENANT_CATALOG = Duration.ofMinutes(30);
    public static final Duration SESSION_META = Duration.ofDays(7);
    public static final Duration RATE_LIMIT_BUCKET = Duration.ofMinutes(10);

    private CacheTtlPolicy() {
    }
}