package com.enterprise.auth.platform.modules.dashboard.interfaces;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

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
        @Schema(description = "24 小时内操作日志数量") long recentOperationLogCount,
        @Schema(description = "今日登录成功次数") long todayLoginCount,
        @Schema(description = "当前在线用户数量") long onlineUserCount,
        @Schema(description = "今日操作日志数量") long todayOperationLogCount,
        @Schema(description = "今日登录失败次数") long todayLoginFailedCount,
        @Schema(description = "今日风险事件数量") long todayRiskEventCount,
        @Schema(description = "近 7 天运行趋势") List<DailyTrendPoint> dailyTrend,
        @Schema(description = "服务健康摘要") List<ServiceHealthItem> serviceHealth,
        @Schema(description = "最近审计事件") List<RecentAuditEvent> recentAuditEvents
) {

    @Schema(description = "每日趋势点")
    public record DailyTrendPoint(
            @Schema(description = "日期，格式 yyyy-MM-dd") String date,
            @Schema(description = "登录成功次数") long loginCount,
            @Schema(description = "操作日志数量") long operationCount,
            @Schema(description = "登录失败次数") long loginFailedCount
    ) {
    }

    @Schema(description = "服务健康项")
    public record ServiceHealthItem(
            @Schema(description = "健康项编码") String code,
            @Schema(description = "健康项名称") String name,
            @Schema(description = "健康状态：UP/DOWN/DEGRADED") String status,
            @Schema(description = "状态说明") String message
    ) {
    }

    @Schema(description = "最近审计事件")
    public record RecentAuditEvent(
            @Schema(description = "事件类型") String eventType,
            @Schema(description = "操作人") String operator,
            @Schema(description = "租户 ID") String tenantId,
            @Schema(description = "客户端 IP") String clientIp,
            @Schema(description = "发生时间，epoch 毫秒") Long occurredAt
    ) {
    }
}
