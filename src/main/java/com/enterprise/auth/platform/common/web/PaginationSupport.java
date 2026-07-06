package com.enterprise.auth.platform.common.web;

/**
 * 分页入参归一化工具：统一各模块对 page/size 的防御性截断写法。
 * 无状态纯函数，不依赖 Spring 容器。
 *
 * <p>各模块历史上限/默认值不同（如 100、200、无上限、缺省 20），由调用方通过参数表达，本类不做数值统一。</p>
 */
public final class PaginationSupport {

    private PaginationSupport() {
    }

    /** 页码归一：不足 1 时取 1（1 基页码）。 */
    public static int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /** 页大小归一：非正数取 1，无上限（历史行为，调用方自行控制上限）。 */
    public static int normalizeSize(int size) {
        return Math.max(size, 1);
    }

    /** 页大小归一：非正数取 1，超过 maxSize 时截断为 maxSize。 */
    public static int normalizeSize(int size, int maxSize) {
        return Math.min(Math.max(size, 1), maxSize);
    }

    /** 页大小归一：非正数取 defaultSize，正数超过 maxSize 时截断为 maxSize。 */
    public static int normalizeSize(int size, int defaultSize, int maxSize) {
        if (size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }
}
