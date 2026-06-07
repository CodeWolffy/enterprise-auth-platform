package com.enterprise.auth.platform.common.context;

import java.util.function.Supplier;
import org.springframework.util.StringUtils;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** 在指定租户上下文内执行，执行完成后恢复原上下文。 */
    public static <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previous = CURRENT.get();
        try {
            CURRENT.set(StringUtils.hasText(tenantId) ? tenantId : "platform");
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previous)) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
            }
        }
    }
}