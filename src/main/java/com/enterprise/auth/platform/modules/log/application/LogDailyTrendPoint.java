package com.enterprise.auth.platform.modules.log.application;

import java.time.Instant;

/** 日志统计投影：供 dashboard 消费，避免 log 反向依赖 dashboard DTO。 */
public record LogDailyTrendPoint(
        String date,
        long loginCount,
        long operationCount,
        long loginFailedCount
) {
}