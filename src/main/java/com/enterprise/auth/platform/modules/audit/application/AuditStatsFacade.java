package com.enterprise.auth.platform.modules.audit.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.audit.infrastructure.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.modules.audit.infrastructure.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.DailyTrendPoint;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.RecentAuditEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AuditStatsFacade {

    private final SysAuditLogMapper sysAuditLogMapper;

    public AuditStatsFacade(SysAuditLogMapper sysAuditLogMapper) {
        this.sysAuditLogMapper = sysAuditLogMapper;
    }

    public long countOperationLogs(
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

    public long countOperationLogs(
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

    public long countOperationLogs(
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

    public long countOperationLogs(
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

    public List<DailyTrendPoint> dailyTrend(
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

    public List<RecentAuditEvent> recentAuditEvents(
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
}