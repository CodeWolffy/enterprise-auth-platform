package com.enterprise.auth.platform.modules.dashboard.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.audit.application.AuditStatsFacade;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.DailyTrendPoint;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.RecentAuditEvent;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.ServiceHealthItem;
import com.enterprise.auth.platform.modules.file.application.FileStatsFacade;
import com.enterprise.auth.platform.modules.role.application.RoleStatsFacade;
import com.enterprise.auth.platform.modules.tenant.application.TenantStatsFacade;
import com.enterprise.auth.platform.modules.user.application.UserStatsFacade;
import java.time.LocalDateTime;
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
    private final AuditStatsFacade auditStatsFacade;
    private final SessionIndexService sessionIndexService;

    public DashboardStatsService(
            CurrentUserService currentUserService,
            PlatformAdminSupport platformAdminSupport,
            DataScopeService dataScopeService,
            UserStatsFacade userStatsFacade,
            RoleStatsFacade roleStatsFacade,
            TenantStatsFacade tenantStatsFacade,
            FileStatsFacade fileStatsFacade,
            AuditStatsFacade auditStatsFacade,
            SessionIndexService sessionIndexService
    ) {
        this.currentUserService = currentUserService;
        this.platformAdminSupport = platformAdminSupport;
        this.dataScopeService = dataScopeService;
        this.userStatsFacade = userStatsFacade;
        this.roleStatsFacade = roleStatsFacade;
        this.tenantStatsFacade = tenantStatsFacade;
        this.fileStatsFacade = fileStatsFacade;
        this.auditStatsFacade = auditStatsFacade;
        this.sessionIndexService = sessionIndexService;
    }

    public DashboardStatsResponse stats() {
        UserAccount user = currentUserService.requireCurrentUser();
        String activeTenantId = activeTenantId(user);
        boolean platformScope = platformAdminSupport.isPlatformSuperAdmin(user) && "platform".equals(activeTenantId);
        String scope = platformScope ? "PLATFORM" : user.dataScopeType().name().equals("ALL") ? "TENANT" : "VISIBLE";
        Optional<Set<Long>> visibleUserIds = platformScope ? Optional.empty() : dataScopeService.visibleUserIds(activeTenantId);
        Optional<Set<String>> visibleUsernames = platformScope ? Optional.empty() : dataScopeService.visibleUsernames(activeTenantId);

        long userCount = userStatsFacade.countUsers(activeTenantId, platformScope, visibleUserIds);
        long roleCount = roleStatsFacade.countRoles(activeTenantId, platformScope);
        long tenantCount = platformScope ? tenantStatsFacade.countTenants() : 1;
        long fileCount = fileStatsFacade.countFiles(activeTenantId, platformScope, visibleUserIds);
        long storageBytes = fileStatsFacade.sumStorageBytes(activeTenantId, platformScope, visibleUserIds);
        long operationLogCount = auditStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, false);
        long recentOperationLogCount = auditStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, true);
        LocalDateTime todayStart = TimeSupport.utcNowDateTime().toLocalDate().atStartOfDay();
        long todayLoginCount = auditStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, "LOGIN_SUCCESS", todayStart, null);
        Optional<Long> onlineUserSnapshot = sessionIndexService.countVisible(activeTenantId, platformScope, visibleUserIds);
        long onlineUserCount = onlineUserSnapshot.orElse(0L);
        long todayOperationLogCount = auditStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, todayStart, null);
        long todayLoginFailedCount = auditStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, "LOGIN_FAILED", todayStart, null);
        long todayRiskEventCount = auditStatsFacade.countOperationLogs(activeTenantId, platformScope, visibleUsernames, Set.of("LOGIN_BLOCKED", "ACCOUNT_LOCKED"), todayStart, null);
        List<DailyTrendPoint> dailyTrend = auditStatsFacade.dailyTrend(activeTenantId, platformScope, visibleUsernames);
        List<ServiceHealthItem> serviceHealth = serviceHealth(onlineUserSnapshot, fileCount, storageBytes);
        List<RecentAuditEvent> recentAuditEvents = auditStatsFacade.recentAuditEvents(activeTenantId, platformScope, visibleUsernames);

        return new DashboardStatsResponse(
                scope,
                platformScope ? null : activeTenantId,
                userCount,
                roleCount,
                tenantCount,
                fileCount,
                storageBytes,
                operationLogCount,
                recentOperationLogCount,
                todayLoginCount,
                onlineUserCount,
                todayOperationLogCount,
                todayLoginFailedCount,
                todayRiskEventCount,
                dailyTrend,
                serviceHealth,
                recentAuditEvents
        );
    }

    private List<ServiceHealthItem> serviceHealth(Optional<Long> onlineUserSnapshot, long fileCount, long storageBytes) {
        return List.of(
                new ServiceHealthItem("backend", "后端服务", "UP", "接口响应正常"),
                new ServiceHealthItem("database", "数据库", "UP", "统计查询正常"),
                new ServiceHealthItem(
                        "redis",
                        "Redis 会话",
                        onlineUserSnapshot.isPresent() ? "UP" : "DEGRADED",
                        onlineUserSnapshot.map(count -> "在线会话 " + count + " 个").orElse("会话索引暂不可读")
                ),
                new ServiceHealthItem("storage", "文件存储", "UP", "文件 " + fileCount + " 个 · " + storageBytes + " B")
        );
    }

    private String activeTenantId(UserAccount user) {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        return StringUtils.hasText(user.tenantId()) ? user.tenantId() : "platform";
    }
}