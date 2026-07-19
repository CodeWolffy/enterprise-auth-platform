package com.enterprise.auth.platform.common.outbox;

/**
 * Transactional outbox publishing port exposed to business modules.
 */
public interface OutboxEventPublisher {

    long enqueue(String eventType, String tenantId, Object payload);

    long enqueue(String eventType, String tenantId, String aggregateType, String aggregateId, Object payload);
}
