package com.enterprise.auth.platform.modules.log.infrastructure.mapper;

import lombok.Data;

@Data
public class LogDailyAggregateRow {
    private String dayKey;
    private Long loginCount;
    private Long loginFailedCount;
    private Long operationCount;
}
