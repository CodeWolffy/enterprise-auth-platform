package com.enterprise.auth.platform.modules.dashboard.domain;

import java.time.Instant;
import java.util.List;

/**
 * 仪表盘统计领域模型。
 *
 * <p>聚合各模块统计门面（StatsFacade）产出的指标，按业务含义分组：
 * 统计作用域、目录规模、文件存储、审计活跃度、在线会话、近 7 天趋势与最近审计事件。
 * 纯数据 + 简单派生逻辑，不依赖 Spring 与 Web 视图；HTTP 视图映射见 application 层。</p>
 */
public record DashboardMetrics(
        StatsScope scope,
        DirectoryCounts directory,
        StorageUsage storage,
        ActivitySummary activity,
        OnlineSessions sessions,
        List<DailyActivity> dailyTrend,
        List<AuditEvent> recentAuditEvents
) {

    /**
     * 服务健康摘要：由会话索引可用性与存储指标派生。
     */
    public List<ServiceHealth> serviceHealth() {
        return List.of(
                new ServiceHealth("backend", "后端服务", "UP", "接口响应正常"),
                new ServiceHealth("database", "数据库", "UP", "统计查询正常"),
                new ServiceHealth(
                        "redis",
                        "Redis 会话",
                        sessions.indexAvailable() ? "UP" : "DEGRADED",
                        sessions.indexAvailable()
                                ? "在线会话 " + sessions.onlineUserCount() + " 个"
                                : "会话索引暂不可读"
                ),
                new ServiceHealth(
                        "storage",
                        "文件存储",
                        "UP",
                        "文件 " + storage.fileCount() + " 个 · " + storage.totalBytes() + " B"
                )
        );
    }

    /**
     * 统计作用域：PLATFORM/TENANT/VISIBLE；平台全局统计时 tenantId 为 null。
     */
    public record StatsScope(String scope, String tenantId) {
    }

    /**
     * 目录规模：用户/角色/租户计数。
     */
    public record DirectoryCounts(long userCount, long roleCount, long tenantCount) {
    }

    /**
     * 文件存储占用。
     */
    public record StorageUsage(long fileCount, long totalBytes) {
    }

    /**
     * 审计活跃度：操作日志与登录/风险事件计数。
     */
    public record ActivitySummary(
            long operationLogCount,
            long recentOperationLogCount,
            long todayLoginCount,
            long todayOperationLogCount,
            long todayLoginFailedCount,
            long todayRiskEventCount
    ) {
    }

    /**
     * 在线会话快照；snapshot 为 null 表示会话索引不可读。
     */
    public record OnlineSessions(Long snapshot) {

        public boolean indexAvailable() {
            return snapshot != null;
        }

        public long onlineUserCount() {
            return snapshot == null ? 0L : snapshot;
        }
    }

    /**
     * 单日活跃度趋势点，date 格式 yyyy-MM-dd。
     */
    public record DailyActivity(String date, long loginCount, long operationCount, long loginFailedCount) {
    }

    /**
     * 服务健康项。
     */
    public record ServiceHealth(String code, String name, String status, String message) {
    }

    /**
     * 审计事件。
     */
    public record AuditEvent(String eventType, String operator, String tenantId, String clientIp, Instant occurredAt) {
    }
}
