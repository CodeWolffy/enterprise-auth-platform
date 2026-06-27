package com.enterprise.auth.platform.common.context;

import java.util.function.Supplier;
import org.springframework.util.StringUtils;

public final class TenantContext {

    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT.set(Scope.tenant(tenantId));
    }

    public static void setGlobalScope(String fallbackTenantId) {
        CURRENT.set(Scope.global(fallbackTenantId));
    }

    public static String getTenantId() {
        Scope scope = CURRENT.get();
        return scope == null ? null : scope.tenantId();
    }

    public static boolean isGlobalScope() {
        Scope scope = CURRENT.get();
        return scope != null && scope.global();
    }

    public static boolean isTenantFilterIgnored() {
        return isGlobalScope();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 在指定租户上下文内执行，执行完成后恢复原上下文。 */
    public static <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        Scope previous = CURRENT.get();
        try {
            CURRENT.set(Scope.tenant(StringUtils.hasText(tenantId) ? tenantId : "platform"));
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    public static <T> T runWithGlobalScope(String fallbackTenantId, Supplier<T> supplier) {
        Scope previous = CURRENT.get();
        try {
            CURRENT.set(Scope.global(fallbackTenantId));
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(Scope previous) {
        if (previous != null) {
            CURRENT.set(previous);
        } else {
            CURRENT.remove();
        }
    }

    private record Scope(String tenantId, boolean global) {
        static Scope tenant(String tenantId) {
            return new Scope(tenantId, false);
        }

        static Scope global(String fallbackTenantId) {
            return new Scope(StringUtils.hasText(fallbackTenantId) ? fallbackTenantId : "platform", true);
        }
    }
}