package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.outbox.OutboxEventPublisher;
import com.enterprise.auth.platform.common.outbox.OutboxEventTypes;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysOutboxEventMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 事务 Outbox 写入端：业务事务内只落库，提交后由 worker 投递。
 */
@Service
public class OutboxWriter implements OutboxEventPublisher {

    public static final String TYPE_NOTIFICATION_PUBLISH = OutboxEventTypes.NOTIFICATION_PUBLISH;
    public static final String TYPE_PASSWORD_RESET_MAIL = OutboxEventTypes.PASSWORD_RESET_MAIL;

    private static final Logger log = LoggerFactory.getLogger(OutboxWriter.class);

    private final SysOutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final OutboxPayloadProtectionService payloadProtectionService;

    public OutboxWriter(
            SysOutboxEventMapper outboxEventMapper,
            ObjectMapper objectMapper,
            OutboxPayloadProtectionService payloadProtectionService
    ) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
        this.payloadProtectionService = payloadProtectionService;
    }

    @Transactional
    @Override
    public long enqueue(String eventType, String tenantId, Object payload) {
        return enqueue(eventType, tenantId, null, null, payload);
    }

    @Transactional
    @Override
    public long enqueue(
            String eventType,
            String tenantId,
            String aggregateType,
            String aggregateId,
            Object payload
    ) {
        if (!StringUtils.hasText(eventType) || payload == null) {
            return 0L;
        }
        SysOutboxEventEntity entity = new SysOutboxEventEntity();
        entity.setTenantId(StringUtils.hasText(tenantId) ? tenantId.trim() : "platform");
        entity.setEventType(eventType.trim());
        entity.setAggregateType(aggregateType);
        entity.setAggregateId(aggregateId);
        entity.setPayloadJson(payloadProtectionService.protect(entity.getEventType(), toJson(payload)));
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        entity.setMaxAttempts(8);
        entity.setNextRetryAt(TimeSupport.now());
        outboxEventMapper.insert(entity);
        return entity.getId() == null ? 0L : entity.getId();
    }

    private String toJson(Object payload) {
        try {
            if (payload instanceof String text) {
                return text;
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            log.error("Outbox 载荷序列化失败: {}", ex.getMessage());
            throw new IllegalStateException("outbox payload serialize failed", ex);
        }
    }
}
