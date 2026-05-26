package com.enterprise.auth.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.dto.model.AuditEvent;
import com.enterprise.auth.platform.dto.resp.AuditPage;
import com.enterprise.auth.platform.dto.req.AuditQuery;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.context.RequestContext;
import com.enterprise.auth.platform.dao.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.dao.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.common.context.AuthContextHolder;
import com.enterprise.auth.platform.common.authz.DataScopeService;
import com.enterprise.auth.platform.common.context.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final long MAX_EXPORT_RANGE_MS = Duration.ofDays(31).toMillis();

    private final SysAuditLogMapper sysAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;

    public AuditService(
            SysAuditLogMapper sysAuditLogMapper,
            ObjectMapper objectMapper,
            DataScopeService dataScopeService
    ) {
        this.sysAuditLogMapper = sysAuditLogMapper;
        this.objectMapper = objectMapper;
        this.dataScopeService = dataScopeService;
    }

    public void record(String type, String operator, String tenantId, Map<String, Object> details) {
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
        Map<String, Object> enrichedDetails = enrichDetails(details, operator, resolvedTenantId);

        SysAuditLogEntity entity = new SysAuditLogEntity();
        entity.setTenantId(resolvedTenantId);
        entity.setEventType(type);
        entity.setOperator(operator);
        entity.setOccurredAt(TimeSupport.utcNowDateTime());
        entity.setRequestId(stringValue(enrichedDetails.get("requestId")));
        entity.setClientIp(stringValue(enrichedDetails.get("clientIp")));
        entity.setPayloadJson(toJson(enrichedDetails));
        sysAuditLogMapper.insert(entity);
    }

    public List<AuditEvent> list() {
        return query(new AuditQuery(null, null, null, null, null, null, null, 1, 100)).records();
    }

    public AuditPage query(AuditQuery query) {
        validateQueryRange(query);
        return queryInDatabase(query);
    }

    public List<AuditEvent> export(AuditQuery query) {
        validateExportQuery(query);
        AuditQuery exportQuery = new AuditQuery(
                query.tenantId(),
                query.eventType(),
                query.operator(),
                query.requestId(),
                query.clientIp(),
                query.fromEpochMs(),
                query.toEpochMs(),
                1,
                2000
        );
        return query(exportQuery).records();
    }

    public void validateExportQuery(AuditQuery query) {
        if (query.fromEpochMs() == null || query.toEpochMs() == null) {
            throw new BusinessException("导出审计记录必须指定开始和结束时间");
        }
        validateQueryRange(query);
    }

    private void validateQueryRange(AuditQuery query) {
        if (query.fromEpochMs() == null || query.toEpochMs() == null) {
            return;
        }
        if (query.fromEpochMs() >= query.toEpochMs()) {
            throw new BusinessException("查询开始时间必须小于结束时间");
        }
        if (query.toEpochMs() - query.fromEpochMs() > MAX_EXPORT_RANGE_MS) {
            throw new BusinessException("审计查询时间范围不能超过 31 天");
        }
    }

    private AuditPage queryInDatabase(AuditQuery query) {
        LambdaQueryWrapper<SysAuditLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.tenantId())) {
            wrapper.eq(SysAuditLogEntity::getTenantId, query.tenantId());
        }
        if (StringUtils.hasText(query.eventType())) {
            wrapper.eq(SysAuditLogEntity::getEventType, query.eventType());
        }
        if (StringUtils.hasText(query.operator())) {
            wrapper.like(SysAuditLogEntity::getOperator, query.operator());
        }
        if (StringUtils.hasText(query.requestId())) {
            wrapper.eq(SysAuditLogEntity::getRequestId, query.requestId());
        }
        if (StringUtils.hasText(query.clientIp())) {
            wrapper.eq(SysAuditLogEntity::getClientIp, query.clientIp());
        }
        dataScopeService.visibleUsernames(queryTenantId(query)).ifPresent(visibleUsers -> {
            if (visibleUsers.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(SysAuditLogEntity::getOperator, visibleUsers);
            }
        });
        if (query.fromEpochMs() != null) {
            wrapper.ge(SysAuditLogEntity::getOccurredAt, TimeSupport.localDateTimeFromEpochMilli(query.fromEpochMs()));
        }
        if (query.toEpochMs() != null) {
            wrapper.lt(SysAuditLogEntity::getOccurredAt, TimeSupport.localDateTimeFromEpochMilli(query.toEpochMs()));
        }
        wrapper.orderByDesc(SysAuditLogEntity::getOccurredAt).orderByDesc(SysAuditLogEntity::getId);

        long total = sysAuditLogMapper.selectCount(wrapper);
        int page = query.normalizedPage();
        int size = query.normalizedSize();
        int offset = (page - 1) * size;
        List<AuditEvent> records = sysAuditLogMapper.selectList(wrapper.last("limit " + offset + "," + size)).stream()
                .map(this::toEvent)
                .toList();
        return new AuditPage(total, page, size, records);
    }

    private Map<String, Object> enrichDetails(Map<String, Object> details, String operator, String resolvedTenantId) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (details != null) {
            enriched.putAll(details);
        }
        String activeTenantId = StringUtils.hasText(TenantContext.getTenantId()) ? TenantContext.getTenantId() : resolvedTenantId;
        String operatorTenantId = AuthContextHolder.currentSession()
                .map(session -> session.operatorTenantId())
                .filter(StringUtils::hasText)
                .orElse(activeTenantId);
        enriched.putIfAbsent("requestId", RequestContext.getRequestId());
        enriched.putIfAbsent("clientIp", RequestContext.getClientIp());
        enriched.putIfAbsent("operator", operator);
        enriched.putIfAbsent("activeTenantId", activeTenantId);
        enriched.putIfAbsent("operatorTenantId", operatorTenantId);
        enriched.putIfAbsent("targetTenantId", resolvedTenantId);
        return enriched;
    }

    private AuditEvent toEvent(SysAuditLogEntity entity) {
        return new AuditEvent(
                entity.getEventType(),
                entity.getOperator(),
                entity.getTenantId(),
                entity.getRequestId(),
                entity.getClientIp(),
                TimeSupport.toEpochMilli(entity.getOccurredAt()),
                parsePayload(entity.getPayloadJson())
        );
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (!StringUtils.hasText(payloadJson)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payloadJson, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("raw", payloadJson);
        }
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String queryTenantId(AuditQuery query) {
        if (StringUtils.hasText(query.tenantId())) {
            return query.tenantId();
        }
        return StringUtils.hasText(TenantContext.getTenantId()) ? TenantContext.getTenantId() : "platform";
    }
}
