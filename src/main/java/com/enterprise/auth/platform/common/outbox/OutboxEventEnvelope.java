package com.enterprise.auth.platform.common.outbox;

/**
 * Decrypted outbox event passed to a registered handler.
 */
public record OutboxEventEnvelope(
        Long id,
        String tenantId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payloadJson
) {
}
