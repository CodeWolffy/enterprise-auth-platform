package com.enterprise.auth.platform.modules.log.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.application.DataScopeService;
import com.enterprise.auth.platform.modules.auth.application.PlatformAdminSupport;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.enterprise.auth.platform.common.context.TimeZoneContext;
import com.enterprise.auth.platform.modules.log.application.LogDailyTrendPoint;
import com.enterprise.auth.platform.modules.log.application.LogRecentAuditEvent;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.entity.SysLoginLogEntity;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLogMapper;
import com.enterprise.auth.platform.modules.log.infrastructure.mapper.SysLoginLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
            wrapper.ge(SysLogEntity::getCreatedAt, TimeSupport.now().minus(Duration.ofDays(1)));
        }
        return sysLogMapper.selectCount(wrapper);
    }

    public long countOperationLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                    Instant fromInclusive, Instant toExclusive) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLogMapper.selectCount(wrapper);
    }

    public long countOperationLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                    String eventType, Instant fromInclusive, Instant toExclusive) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames)
                .eq(SysLogEntity::getEventType, eventType);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLogMapper.selectCount(wrapper);
    }

    public long countOperationLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                    Set<String> eventTypes, Instant fromInclusive, Instant toExclusive) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames)
                .in(SysLogEntity::getEventType, eventTypes);
        applyTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLogMapper.selectCount(wrapper);
    }

    public long countLoginLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                Instant fromInclusive, Instant toExclusive) {
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = loginScope(tenantId, platformScope, visibleUsernames);
        applyLoginTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLoginLogMapper.selectCount(wrapper);
    }

    public long countLoginLogs(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames,
                                String status, Instant fromInclusive, Instant toExclusive) {
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = loginScope(tenantId, platformScope, visibleUsernames)
                .eq(SysLoginLogEntity::getStatus, status);
        applyLoginTimeRange(wrapper, fromInclusive, toExclusive);
        return sysLoginLogMapper.selectCount(wrapper);
    }

    public List<LogDailyTrendPoint> dailyTrend(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames) {
        ZoneId zone = TimeZoneContext.getZone();
        LocalDate startDate = TimeSupport.today(zone).minusDays(6);
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return java.util.stream.IntStream.range(0, 7)
                .mapToObj(offset -> {
                    LocalDate date = startDate.plusDays(offset);
                    Instant from = TimeSupport.startOfDay(date, zone);
                    Instant to = TimeSupport.startOfDay(date.plusDays(1), zone);
                    return new LogDailyTrendPoint(
                            formatter.format(date),
                            countLoginLogs(tenantId, platformScope, visibleUsernames, "SUCCESS", from, to),
                            countOperationLogs(tenantId, platformScope, visibleUsernames, from, to),
                            countLoginLogs(tenantId, platformScope, visibleUsernames, "FAILED", from, to)
                    );
                })
                .toList();
    }

    public List<LogRecentAuditEvent> recentAuditEvents(String tenantId, boolean platformScope, Optional<Set<String>> visibleUsernames) {
        LambdaQueryWrapper<SysLogEntity> wrapper = logScope(tenantId, platformScope, visibleUsernames)
                .orderByDesc(SysLogEntity::getCreatedAt)
                .last("LIMIT 10");
        return sysLogMapper.selectList(wrapper).stream()
                .map(entity -> new LogRecentAuditEvent(
                        entity.getEventType(),
                        entity.getOperator(),
                        entity.getTenantId(),
                        entity.getClientIp(),
                        entity.getCreatedAt()
                ))
                .toList();
    }

    private LambdaQueryWrapper<SysLogEntity> logScope(String tenantId, boolean platformScope,
                                                       Optional<Set<String>> visibleUsernames) {
        LambdaQueryWrapper<SysLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(SysLogEntity::getTenantId, tenantId);
        }
        if (!platformScope && visibleUsernames.isPresent()) {
            Set<String> usernames = visibleUsernames.get();
            if (usernames.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysLogEntity::getOperator, usernames);
            }
        }
        return wrapper;
    }

    private LambdaQueryWrapper<SysLoginLogEntity> loginScope(String tenantId, boolean platformScope,
                                                              Optional<Set<String>> visibleUsernames) {
        LambdaQueryWrapper<SysLoginLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(SysLoginLogEntity::getTenantId, tenantId);
        }
        if (!platformScope && visibleUsernames.isPresent()) {
            Set<String> usernames = visibleUsernames.get();
            if (usernames.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysLoginLogEntity::getUserName, usernames);
            }
        }
        return wrapper;
    }

    private void applyTimeRange(LambdaQueryWrapper<SysLogEntity> wrapper, Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive != null) {
            wrapper.ge(SysLogEntity::getCreatedAt, fromInclusive);
        }
        if (toExclusive != null) {
            wrapper.lt(SysLogEntity::getCreatedAt, toExclusive);
        }
    }

    private void applyLoginTimeRange(LambdaQueryWrapper<SysLoginLogEntity> wrapper, Instant fromInclusive, Instant toExclusive) {
        if (fromInclusive != null) {
            wrapper.ge(SysLoginLogEntity::getCreatedAt, fromInclusive);
        }
        if (toExclusive != null) {
            wrapper.lt(SysLoginLogEntity::getCreatedAt, toExclusive);
        }
    }
}
