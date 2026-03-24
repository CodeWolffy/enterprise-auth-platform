package com.enterprise.auth.platform.audit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.enterprise.auth.platform.audit.model.AuditEvent;
import com.enterprise.auth.platform.audit.model.AuditPage;
import com.enterprise.auth.platform.audit.model.AuditQuery;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.web.RequestContext;
import com.enterprise.auth.platform.config.PersistenceProperties;
import com.enterprise.auth.platform.persistence.entity.SysAuditLogEntity;
import com.enterprise.auth.platform.persistence.mapper.SysAuditLogMapper;
import com.enterprise.auth.platform.security.DataScopeService;
import com.enterprise.auth.platform.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuditService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final PersistenceProperties persistenceProperties;
    private final SysAuditLogMapper sysAuditLogMapper;
    private final ObjectMapper objectMapper;
    private final DataScopeService dataScopeService;

    public AuditService(
            PersistenceProperties persistenceProperties,
            @Nullable SysAuditLogMapper sysAuditLogMapper,
            ObjectMapper objectMapper,
            DataScopeService dataScopeService
    ) {
        this.persistenceProperties = persistenceProperties;
        this.sysAuditLogMapper = sysAuditLogMapper;
        this.objectMapper = objectMapper;
        this.dataScopeService = dataScopeService;
    }

    public void record(String type, String operator, String tenantId, Map<String, Object> details) {
        if (!databaseEnabled()) {
            return;
        }
        Instant now = Instant.now();
        String resolvedTenantId = StringUtils.hasText(tenantId) ? tenantId : "platform";
        Map<String, Object> enrichedDetails = enrichDetails(details);

        SysAuditLogEntity entity = new SysAuditLogEntity();
        entity.setTenantId(resolvedTenantId);
        entity.setEventType(type);
        entity.setOperator(operator);
        entity.setOccurredAt(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
        entity.setRequestId(stringValue(enrichedDetails.get("requestId")));
        entity.setClientIp(stringValue(enrichedDetails.get("clientIp")));
        entity.setPayloadJson(toJson(enrichedDetails));
        sysAuditLogMapper.insert(entity);
    }

    public List<AuditEvent> list() {
        return query(new AuditQuery(null, null, null, null, null, null, null, 1, 100)).records();
    }

    public AuditPage query(AuditQuery query) {
        if (!databaseEnabled()) {
            throw new BusinessException("当前未启用数据库审计存储");
        }
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
                query.occurredFrom(),
                query.occurredTo(),
                1,
                2000
        );
        return query(exportQuery).records();
    }

    public void validateExportQuery(AuditQuery query) {
        if (query.occurredFrom() == null || query.occurredTo() == null) {
            throw new BusinessException("导出审计记录必须指定开始和结束时间");
        }
        if (query.occurredTo().isBefore(query.occurredFrom())) {
            throw new BusinessException("审计导出结束时间不能早于开始时间");
        }
        long days = java.time.Duration.between(query.occurredFrom(), query.occurredTo()).toDays();
        if (days > 31) {
            throw new BusinessException("审计导出时间范围不能超过 31 天");
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
        if (query.occurredFrom() != null) {
            wrapper.ge(SysAuditLogEntity::getOccurredAt, LocalDateTime.ofInstant(query.occurredFrom(), ZoneId.systemDefault()));
        }
        if (query.occurredTo() != null) {
            wrapper.le(SysAuditLogEntity::getOccurredAt, LocalDateTime.ofInstant(query.occurredTo(), ZoneId.systemDefault()));
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

    private Map<String, Object> enrichDetails(Map<String, Object> details) {
        Map<String, Object> enriched = new LinkedHashMap<>();
        if (details != null) {
            enriched.putAll(details);
        }
        enriched.putIfAbsent("requestId", RequestContext.getRequestId());
        enriched.putIfAbsent("clientIp", RequestContext.getClientIp());
        return enriched;
    }

    private AuditEvent toEvent(SysAuditLogEntity entity) {
        return new AuditEvent(
                entity.getEventType(),
                entity.getOperator(),
                entity.getTenantId(),
                entity.getRequestId(),
                entity.getClientIp(),
                entity.getOccurredAt().atZone(ZoneId.systemDefault()).toInstant(),
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

    private boolean databaseEnabled() {
        return persistenceProperties.databaseEnabled() && sysAuditLogMapper != null;
    }

    private String queryTenantId(AuditQuery query) {
        if (StringUtils.hasText(query.tenantId())) {
            return query.tenantId();
        }
        return StringUtils.hasText(TenantContext.getTenantId()) ? TenantContext.getTenantId() : "platform";
    }
}
