package com.enterprise.auth.platform.modules.dashboard.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "仪表盘统计结果")
public record DashboardStatsResponse(
        @Schema(description = "统计作用域：PLATFORM/TENANT/VISIBLE") String scope,
        @Schema(description = "当前租户 ID，平台全局统计时为空") String tenantId,
        @Schema(description = "用户数量") long userCount,
        @Schema(description = "角色数量") long roleCount,
        @Schema(description = "租户数量") long tenantCount,
        @Schema(description = "文件数量") long fileCount,
        @Schema(description = "文件总大小，单位字节") long storageBytes,
        @Schema(description = "操作日志数量") long operationLogCount,
        @Schema(description = "24 小时内操作日志数量") long recentOperationLogCount
) {
}