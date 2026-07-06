package com.enterprise.auth.platform.common.context;

import org.springframework.util.StringUtils;

/**
 * 租户上下文读取归一工具：统一各模块「{@link TenantContext#getTenantId()} 为空时回退」的重复写法。
 * 无状态纯函数，不依赖 Spring 容器。
 *
 * <p>回退值由调用方给定（如 {@link #PLATFORM_TENANT_ID}、user.tenantId()、可配置的平台租户号），
 * 以避免 common 反向依赖任何 modules 下的类型。注意回退实参会被立即求值，仅适合传入无副作用的取值表达式。</p>
 */
public final class TenantContextSupport {

    /** 平台（宿主）租户号默认值，与 {@link TenantContext} 内部回退值一致。 */
    public static final String PLATFORM_TENANT_ID = "platform";

    private TenantContextSupport() {
    }

    /** 上下文租户号有文本时原样返回（不 trim），否则返回 fallback。 */
    public static String currentTenantIdOr(String fallback) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : fallback;
    }

    /** 上下文租户号有文本时返回 trim 后的值，否则返回 fallback。 */
    public static String currentTenantIdTrimmedOr(String fallback) {
        String tenantId = TenantContext.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId.trim() : fallback;
    }

    /** 最常见变体：上下文租户号为空时回退平台租户号 {@link #PLATFORM_TENANT_ID}。 */
    public static String currentTenantIdOrPlatform() {
        return currentTenantIdOr(PLATFORM_TENANT_ID);
    }
}
