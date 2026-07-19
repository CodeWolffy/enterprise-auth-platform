package com.enterprise.auth.platform.common.outbox;

/**
 * Extensible outbox handler. Implementations must be idempotent because an event may be delivered more than once.
 */
public interface OutboxEventHandler {

    String eventType();

    void handle(OutboxEventEnvelope event) throws Exception;
}
