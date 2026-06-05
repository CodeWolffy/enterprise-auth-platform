package com.enterprise.auth.platform.modules.dashboard.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.audit.infrastructure.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.modules.audit.infrastructure.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.modules.auth.application.CurrentUserService;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import com.enterprise.auth.platform.modules.auth.domain.UserAccount;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.DailyTrendPoint;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.RecentAuditEvent;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.ServiceHealthItem;
import com.enterprise.auth.platform.modules.file.infrastructure.entity.SysStorageFileEntity;
import com.enterprise.auth.platform.modules.file.infrastructure.mapper.SysStorageFileMapper;
import com.enterprise.auth.platform.modules.role.infrastructure.entity.SysRoleEntity;
import com.enterprise.auth.platform.modules.role.infrastructure.mapper.SysRoleMapper;
import com.enterprise.auth.platform.modules.tenant.infrastructure.entity.SysTenantEntity;
import com.enterprise.auth.platform.modules.tenant.infrastructure.mapper.SysTenantMapper;
import com.enterprise.auth.platform.modules.user.infrastructure.entity.SysUserEntity;
import com.enterprise.auth.platform.modules.user.infrastructure.mapper.SysUserMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysTenantMapper sysTenantMapper;
    private final SysStorageFileMapper sysStorageFileMapper;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final SessionIndexService sessionIndexService;

    public DashboardStatsService(
            CurrentUserService currentUserService,
            PlatformAdminSupport platformAdminSupport,
            DataScopeService dataScopeService,
            SysUserMapper sysUserMapper,
            SysRoleMapper sysRoleMapper,
            SysTenantMapper sysTenantMapper,
            SysStorageFileMapper sysStorageFileMapper,
            SysAuditLogMapper sysAuditLogMapper,
            SessionIndexService sessionIndexService
    ) {
        this.currentUserService = currentUserService;
        this.platformAdminSupport = platformAdminSupport;
        this.dataScopeService = dataScopeService;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysTenantMapper = sysTenantMapper;
        this.sysStorageFileMapper = sysStorageFileMapper;
        this.sysAuditLogMapper = sysAuditLogMapper;
        this.sessionIndexService = sessionIndexService;
    }

    public DashboardStatsResponse stats() {
        UserAccount user = currentUserService.requireCurrentUser();
        String activeTenantId = activeTenantId(user);
        boolean platformScope = platformAdminSupport.isPlatformSuperAdmin(user) && "platform".equals(activeTenantId);
        String scope = platformScope ? "PLATFORM" : user.dataScopeType().name().equals("ALL") ? "TENANT" : "VISIBLE";
        Optional<Set<Long>> visibleUserIds = platformScope ? Optional.empty() : dataScopeService.visibleUserIds(activeTenantId);
        Optional<Set<String>> visibleUsernames = platformScope ? Optional.empty() : dataScopeService.visibleUsernames(activeTenantId);

        long userCount = countUsers(activeTenantId, platformScope, visibleUserIds);
        long roleCount = countRoles(activeTenantId, platformScope);
        long tenantCount = platformScope ? countTenants() : 1;
        long fileCount = countFiles(activeTenantId, platformScope, visibleUserIds);
        long storageBytes = sumStorageBytes(activeTenantId, platformScope, visibleUserIds);
        long operationLogCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, false);
        long recentOperationLogCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, true);
        LocalDateTime todayStart = TimeSupport.utcNowDateTime().toLocalDate().atStartOfDay();
        long todayLoginCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, "LOGIN_SUCCESS", todayStart, null);
        Optional<Long> onlineUserSnapshot = sessionIndexService.countVisible(activeTenantId, platformScope, visibleUserIds);
        long onlineUserCount = onlineUserSnapshot.orElse(0L);
        long todayOperationLogCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, todayStart, null);
        long todayLoginFailedCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, "LOGIN_FAILED", todayStart, null);
        long todayRiskEventCount = countOperationLogs(activeTenantId, platformScope, visibleUsernames, Set.of("LOGIN_BLOCKED", "ACCOUNT_LOCKED"), todayStart, null);
        List<DailyTrendPoint> dailyTrend = dailyTrend(activeTenantId, platformScope, visibleUsernames);
        List<ServiceHealthItem> serviceHealth = serviceHealth(onlineUserSnapshot, fileCount, storageBytes);
        List<RecentAuditEvent> recentAuditEvents = recentAuditEvents(activeTenantId, platformScope, visibleUsernames);

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

    private long countUsers(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysUserEntity> wrapper = new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysUserEntity::getTenantId, tenantId);
            applyVisibleUserIds(wrapper, visibleUserIds, SysUserEntity::getId);
        }
        return sysUserMapper.selectCount(wrapper);
    }

    private long countRoles(String tenantId, boolean platformScope) {
        LambdaQueryWrapper<SysRoleEntity> wrapper = new LambdaQueryWrapper<SysRoleEntity>()
                .eq(SysRoleEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysRoleEntity::getTenantId, tenantId);
        }
        return sysRoleMapper.selectCount(wrapper);
    }

    private long countTenants() {
        return sysTenantMapper.selectCount(new LambdaQueryWrapper<SysTenantEntity>()
                .eq(SysTenantEntity::getDeleted, 0));
    }

    private long countFiles(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = fileScope(tenantId, platformScope, visibleUserIds);
        return sysStorageFileMapper.selectCount(wrapper);
    }

    private long sumStorageBytes(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        Long totalSize = sysStorageFileMapper.sumFileSize(
                tenantId,
                platformScope,
                visibleUserIds.orElse(null)
        );
        return totalSize == null ? 0L : Math.max(totalSize, 0L);
    }

    private LambdaQueryWrapper<SysStorageFileEntity> fileScope(String tenantId, boolean platformScope, Optional<Set<Long>> visibleUserIds) {
        LambdaQueryWrapper<SysStorageFileEntity> wrapper = new LambdaQueryWrapper<SysStorageFileEntity>()
                .eq(SysStorageFileEntity::getDeleted, 0);
        if (!platformScope) {
            wrapper.eq(SysStorageFileEntity::getTenantId, tenantId);
            applyVisibleUserIds(wrapper, visibleUserIds, SysStorageFileEntity::getOwnerUserId);
        }
        return wrapper;
    }

    private long countOperationLogs(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames,
            boolean recentOnly
    ) {
        LambdaQueryWrapper<SysAuditLogEntity> wrapper = auditScope(tenantId, platformScope, visibleUsernames);
        if (recentOnly) {
            wrapper.ge(SysAuditLogEntity::getOccurredAt, TimeSupport.utcNowDateTime().minusDays(1));
        }
        return sysAuditLogMapper.selectCount(wrapper);
    }

    private long countOperationLogs(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        LambdaQueryWrapper<SysAuditLogEntity> wrapper = auditScope(tenantId, platformScope, visibleUsernames);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysAuditLogMapper.selectCount(wrapper);
    }

    private long countOperationLogs(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames,
            String eventType,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        LambdaQueryWrapper<SysAuditLogEntity> wrapper = auditScope(tenantId, platformScope, visibleUsernames)
                .eq(SysAuditLogEntity::getEventType, eventType);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysAuditLogMapper.selectCount(wrapper);
    }

    private long countOperationLogs(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames,
            Set<String> eventTypes,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        LambdaQueryWrapper<SysAuditLogEntity> wrapper = auditScope(tenantId, platformScope, visibleUsernames)
                .in(SysAuditLogEntity::getEventType, eventTypes);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysAuditLogMapper.selectCount(wrapper);
    }

    private List<DailyTrendPoint> dailyTrend(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames
    ) {
        LocalDate startDate = TimeSupport.utcNowDateTime().toLocalDate().minusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(offset -> {
                    LocalDate date = startDate.plusDays(offset);
                    LocalDateTime from = date.atStartOfDay();
                    LocalDateTime to = date.plusDays(1).atStartOfDay();
                    return new DailyTrendPoint(
                            formatter.format(date),
                            countOperationLogs(tenantId, platformScope, visibleUsernames, "LOGIN_SUCCESS", from, to),
                            countOperationLogs(tenantId, platformScope, visibleUsernames, from, to),
                            countOperationLogs(tenantId, platformScope, visibleUsernames, "LOGIN_FAILED", from, to)
                    );
                })
                .toList();
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

    private List<RecentAuditEvent> recentAuditEvents(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames
    ) {
        return sysAuditLogMapper.selectList(auditScope(tenantId, platformScope, visibleUsernames)
                        .orderByDesc(SysAuditLogEntity::getOccurredAt)
                        .orderByDesc(SysAuditLogEntity::getId)
                        .last("limit 6"))
                .stream()
                .map(item -> new RecentAuditEvent(
                        item.getEventType(),
                        item.getOperator(),
                        item.getTenantId(),
                        item.getClientIp(),
                        TimeSupport.toEpochMilli(item.getOccurredAt())
                ))
                .toList();
    }

    private LambdaQueryWrapper<SysAuditLogEntity> auditScope(
            String tenantId,
            boolean platformScope,
            Optional<Set<String>> visibleUsernames
    ) {
        LambdaQueryWrapper<SysAuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (!platformScope) {
            wrapper.eq(SysAuditLogEntity::getTenantId, tenantId);
            applyVisibleUsernames(wrapper, visibleUsernames);
        }
        return wrapper;
    }

    private void applyVisibleUsernames(
            LambdaQueryWrapper<SysAuditLogEntity> wrapper,
            Optional<Set<String>> visibleUsernames
    ) {
        visibleUsernames.ifPresent(usernames -> {
            if (usernames.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysAuditLogEntity::getOperator, usernames);
            }
        });
    }

    private void applyTimeRange(
            LambdaQueryWrapper<SysAuditLogEntity> wrapper,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive
    ) {
        if (fromInclusive != null) {
            wrapper.ge(SysAuditLogEntity::getOccurredAt, fromInclusive);
        }
        if (toExclusive != null) {
            wrapper.lt(SysAuditLogEntity::getOccurredAt, toExclusive);
        }
    }

    private <T> void applyVisibleUserIds(
            LambdaQueryWrapper<T> wrapper,
            Optional<Set<Long>> visibleUserIds,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column
    ) {
        visibleUserIds.ifPresent(userIds -> {
            if (userIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(column, userIds);
            }
        });
    }

    private String activeTenantId(UserAccount user) {
        String tenantId = TenantContext.getTenantId();
        if (StringUtils.hasText(tenantId)) {
            return tenantId;
        }
        return StringUtils.hasText(user.tenantId()) ? user.tenantId() : "platform";
    }
}