package com.enterprise.auth.platform.modules.log.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.authz.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.DailyTrendPoint;
import com.enterprise.auth.platform.modules.dashboard.interfaces.DashboardStatsResponse.RecentAuditEvent;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LogStatsFacade {

    private final SysLogMapper sysLogMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final DataScopeService dataScopeService;
    private final PlatformAdminSupport platformAdminSupport;

    public LogStatsFacade(SysLogMapper sysLogMapper, SysLoginLogMapper sysLoginLogMapper,
                          DataScopeService dataScopeService, PlatformAdminSupport platformAdminSupport) {
        this.sysLogMapper = sysLogMapper;
        this.sysLoginLogMapper = sysLoginLogMapper;
        this.dataScopeService = dataScopeService;
        this.platformAdminSupport = platformAdminSupport;
    }

    public long countOperationLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames, boolean recentOnly) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames);
        if (recentOnly) {
            wrapper.ge(SysLogEntity::getCreatedAt, TimeSupport.utcNowDateTime().minusDays(1));
        }
        return sysLogMapper.selectCount(wrapper);
    }

    public long countOperationLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                    LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLogMapper.selectCount(wrapper);
    }

    public long countOperationLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                    String eventType, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames)
                .eq(SysLogEntity::getEventType, eventType);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLogMapper.selectCount(wrapper);
    }

    public long countOperationLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                    Set<String> eventTypes, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames)
                .in(SysLogEntity::getEventType, eventTypes);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLogMapper.selectCount(wrapper);
    }

    public long countLoginLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = loginScope(tenantId, platformScope, visibleUsernames);
        applyLoginTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLoginLogMapper.selectCount(wrapper);
    }

    public long countLoginLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                String status, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = loginScope(tenantId, platformScope, visibleUsernames)
                .eq(SysLoginLogEntity::getStatus, status);
        applyLoginTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLoginLogMapper.selectCount(wrapper);
    }

    public List<DailyTrendPoint> dailyTrend(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames) {
        LocalDate startDate = TimeSupport.utcNowDateTime().toLocalDate().minusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(offset -> {
                    LocalDate date = startDate.plusDays(offset);
                    LocalDateTime from = date.atStartOfDay();
                    LocalDateTime to = date.plusDays(1).atStartOfDay();
                    return new DailyTrendPoint(
                            formatter.format(date),
                            countLoginLogs(tenantId, platformScope, visibleUsernames, "SUCCESS", from, to),
                            countOperationLogs(tenantId, platformScope, visibleUsernames, from, to),
                            countLoginLogs(tenantId, platformScope, visibleUsernames, "FAILED", from, to)
                    );
                })
                .toList();
    }

    public List<RecentAuditEvent> recentAuditEvents(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames)
                .orderByDesc(SysLogEntity::getCreatedAt)
                .last("LIMIT 10");
        return sysLogMapper.selectList(wrapper).stream()
                .map(entity -> new RecentAuditEvent(
                        entity.getEventType(),
                        entity.getOperator(),
                        entity.getTenantId(),
                        entity.getClientIp(),
                        entity.getCreatedAt() != null ? TimeSupport.toEpochMilli(entity.getCreatedAt()) : null
                ))
                .toList();
    }

    private LambdaQueryWrapper<SysLogEntity> logScope(String tenantId, boolean platformScope,
                                                       Optional<Set<String>> visibleUsernames) {
        LambdaQueryWrapper<SysLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(SysLogEntity::getTenantId, tenantId);
        }
        if (!platformScope && visibleUsernames.isPresent() && !visibleUsernames.get().isEmpty()) {
            wrapper.in(SysLogEntity::getOperator, visibleUsernames.get());
        }
        return wrapper;
    }

    private LambdaQueryWrapper<SysLoginLogEntity> loginScope(String tenantId, boolean platformScope,
                                                              Optional<Set<String>> visibleUsernames) {
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(SysLoginLogEntity::getTenantId, tenantId);
        }
        if (!platformScope && visibleUsernames.isPresent() && !visibleUsernames.get().isEmpty()) {
            wrapper.in(SysLoginLogEntity::getUserName, visibleUsernames.get());
        }
        return wrapper;
    }

    private void applyTimeRange(LambdaQueryWrapper<SysLogEntity> wrapper, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        if (fromInclusive != null) {
            wrapper.ge(SysLogEntity::getCreatedAt, fromInclusive);
        }
        if (toExclusive != null) {
            wrapper.lt(SysLogEntity::getCreatedAt, toExclusive);
        }
    }

    private void applyLoginTimeRange(LambdaQueryWrapper<SysLoginLogEntity> wrapper, LocalDateTime fromInclusive, LocalDateTime toExclusive) {
        if (fromInclusive != null) {
            wrapper.ge(SysLoginLogEntity::getCreatedAt, fromInclusive);
        }
        if (toExclusive != null) {
            wrapper.lt(SysLoginLogEntity::getCreatedAt, toExclusive);
        }
    }
}