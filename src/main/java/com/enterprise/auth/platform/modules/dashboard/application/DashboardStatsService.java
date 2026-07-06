package com.enterprise.auth.platform.modules.dashboard.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.context.TimeZoneContext;
import com.enterprise.auth.platform.modules.log.application.LogStatsFacade;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics.ActivitySummary;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics.AuditEvent;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics.DailyActivity;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics.DirectoryCounts;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics.OnlineSessions;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics.StatsScope;
import com.enterprise.auth.platform.modules.dashboard.domain.DashboardMetrics.StorageUsage;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.DailyTrendPoint;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.RecentAuditEvent;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.ServiceHealthItem;
import com.enterprise.auth.platform.modules.file.application.FileStatsFacade;
import com.enterprise.auth.platform.modules.role.application.RoleStatsFacade;
import com.enterprise.auth.platform.modules.tenant.application.TenantStatsFacade;
import com.enterprise.auth.platform.modules.user.application.UserStatsFacade;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DashboardStatsService {

    private final CurrentUserService currentUserService;
    private final PlatformAdminSupport platformAdminSupport;
    private final DataScopeService dataScopeService;
    private final UserStatsFacade userStatsFacade;
    private final RoleStatsFacade roleStatsFacade;
    private final TenantStatsFacade tenantStatsFacade;
    private final FileStatsFacade fileStatsFacade;
    private final LogStatsFacade logStatsFacade;
    private final SessionIndexService sessionIndexService;

    public DashboardStatsService(
            CurrentUserService currentUserService,
            PlatformAdminSupport platformAdminSupport,
            DataScopeService dataScopeService,
            UserStatsFacade userStatsFacade,
            RoleStatsFacade roleStatsFacade,
            TenantStatsFacade tenantStatsFacade,
            FileStatsFacade fileStatsFacade,
            LogStatsFacade logStatsFacade,
            SessionIndexService sessionIndexService
    ) {
        this.currentUserService = currentUserService;
        this.platformAdminSupport = platformAdminSupport;
        this.dataScopeService = dataScopeService;
        this.userStatsFacade = userStatsFacade;
        this.roleStatsFacade = roleStatsFacade;
        this.tenantStatsFacade = tenantStatsFacade;
        this.fileStatsFacade = fileStatsFacade;
        this.logStatsFacade = logStatsFacade;
        this.sessionIndexService = sessionIndexService;
    }

    public DashboardStatsResponse stats() {
        UserAccount user = currentUserService.requireCurrentUser();
        String activeTenantId = activeTenantId(user);
        boolean platformScope = platformAdminSupport.isPlatformSuperAdmin(user) && "platform".equals(activeTenantId);
        String scope = platformScope ? "PLATFORM" : user.dataScopeType().name().equals("ALL") ? "TENANT" : "VISIBLE";

        DashboardMetrics metrics = collectMetrics(scope, activeTenantId, platformScope);
        return toResponse(metrics);
    }

    private DashboardMetrics collectMetrics(String scope, String activeTenantId, boolean platformScope) {
        Optional<Set<Long>> visibleUserIds = platformScope ? Optional.empty() : dataScopeService.visibleUserIds(activeTenantId);
        Optional<Set<String>> visibleUsernames = platformScope ? Optional.empty() : dataScopeService.visibleUsernames(activeTenantId);

        long userCount = userStatsFacade.countUsers(activeTenantId, platformScope, visibleUserIds);
        long roleCount = roleStatsFacade.countRoles(activeTenantId, platformScope);
        long tenantCount = platformScope ? tenantStatsFacade.countTenants() : 1;
        long fileCount = fileStatsFacade.countFiles(activeTenantId, platformScope, visibleUserIds);
        long storageBytes = fileStatsFacade.sumStorageBytes(activeTenantId, platformScope, visibleUserIds);
        long operationLogCount = logStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, false);
        long recentOperationLogCount = logStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, true);
        ZoneId zone = TimeZoneContext.getZone();
        Instant todayStart = TimeSupport.startOfDay(TimeSupport.today(zone), zone);
        long todayLoginCount = logStatsFacade.countLoginLogs(activeTenantId, platformScope, visibleUsernames, "SUCCESS", todayStart, null);
        Optional<Long> onlineUserSnapshot = sessionIndexService.countVisible(activeTenantId, platformScope, visibleUserIds);
        long todayOperationLogCount = logStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, todayStart, null);
        long todayLoginFailedCount = logStatsFacade.countLoginLogs(activeTenantId, platformScope, visibleUsernames, "FAILED", todayStart, null);
        long todayRiskEventCount = logStatsFacade.countLoginLogs(activeTenantId, platformScope, visibleUsernames, "LOCKED", todayStart, null);

        return new DashboardMetrics(
                new StatsScope(scope, platformScope ? null : activeTenantId),
                new DirectoryCounts(userCount, roleCount, tenantCount),
                new StorageUsage(fileCount, storageBytes),
                new ActivitySummary(
                        operationLogCount,
                        recentOperationLogCount,
                        todayLoginCount,
                        todayOperationLogCount,
                        todayLoginFailedCount,
                        todayRiskEventCount
                ),
                new OnlineSessions(onlineUserSnapshot.orElse(null)),
                toDailyActivities(logStatsFacade.dailyTrend(activeTenantId, platformScope, visibleUsernames)),
                toAuditEvents(logStatsFacade.recentAuditEvents(activeTenantId, platformScope, visibleUsernames))
        );
    }

    private DashboardStatsResponse toResponse(DashboardMetrics metrics) {
        return new DashboardStatsResponse(
                metrics.scope().scope(),
                metrics.scope().tenantId(),
                metrics.directory().userCount(),
                metrics.directory().roleCount(),
                metrics.directory().tenantCount(),
                metrics.storage().fileCount(),
                metrics.storage().totalBytes(),
                metrics.activity().operationLogCount(),
                metrics.activity().recentOperationLogCount(),
                metrics.activity().todayLoginCount(),
                metrics.sessions().onlineUserCount(),
                metrics.activity().todayOperationLogCount(),
                metrics.activity().todayLoginFailedCount(),
                metrics.activity().todayRiskEventCount(),
                metrics.dailyTrend().stream()
                        .map(point -> new DailyTrendPoint(point.date(), point.loginCount(), point.operationCount(), point.loginFailedCount()))
                        .toList(),
                metrics.serviceHealth().stream()
                        .map(item -> new ServiceHealthItem(item.code(), item.name(), item.status(), item.message()))
                        .toList(),
                metrics.recentAuditEvents().stream()
                        .map(event -> new RecentAuditEvent(event.eventType(), event.operator(), event.tenantId(), event.clientIp(), event.occurredAt()))
                        .toList()
        );
    }

    private List<DailyActivity> toDailyActivities(List<DailyTrendPoint> points) {
        return points.stream()
                .map(point -> new DailyActivity(point.date(), point.loginCount(), point.operationCount(), point.loginFailedCount()))
                .toList();
    }

    private List<AuditEvent> toAuditEvents(List<RecentAuditEvent> events) {
        return events.stream()
                .map(event -> new AuditEvent(event.eventType(), event.operator(), event.tenantId(), event.clientIp(), event.occurredAt()))
                .toList();
    }

    private String activeTenantId(UserAccount user) {
        String fallback = StringUtils.hasText(user.tenantId()) ? user.tenantId() : TenantContextSupport.PLATFORM_TENANT_ID;
        return TenantContextSupport.currentTenantIdOr(fallback);
    }
}
