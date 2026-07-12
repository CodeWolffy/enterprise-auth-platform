package com.enterprise.auth.platform.common.web;

/**
 * 分页入参归一化工具：统一各模块对 page/size 的防御性截断写法。
 * 无状态纯函数，不依赖 Spring 容器。
 *
 * <p>管理列表默认硬上限 100；导出等场景应使用独立异步任务，不得抬高本上限。</p>
 */
public final class PaginationSupport {

    public static final int DEFAULT_MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;

    private PaginationSupport() {
    }

    /** 页码归一：不足 1 时取 1（1 基页码）。 */
    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /**
     * 页大小归一：非正数取 1，默认硬上限 {@link #DEFAULT_MAX_SIZE}。
     * 无上限重载已移除，防止资源耗尽入口。
     */
    public static int normalizeSize(int size) {
        return normalizeSize(size, DEFAULT_MAX_SIZE);
    }

    /** 页大小归一：非正数取 1，超过 maxSize 时截断为 maxSize。 */
    public static int normalizeSize(int size, int maxSize) {
        int bound = maxSize <= 0 ? DEFAULT_MAX_SIZE : maxSize;
        return Math.min(Math.max(size, 1), bound);
    }

    /** 页大小归一：非正数取 defaultSize，正数超过 maxSize 时截断为 maxSize。 */
    public static int normalizeSize(int size, int defaultSize, int maxSize) {
        int bound = maxSize <= 0 ? DEFAULT_MAX_SIZE : maxSize;
        if (size <= 0) {
            return Math.min(Math.max(defaultSize, 1), bound);
        }
        return Math.min(size, bound);
    }

    /** 安全计算 offset，避免 int 乘法溢出。 */
    public static long offset(int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        return (long) (safePage - 1) * safeSize;
    }
}