package com.enterprise.auth.platform.modules.notification.application;

import com.enterprise.auth.platform.common.outbox.OutboxEventEnvelope;
import com.enterprise.auth.platform.common.outbox.OutboxEventHandler;
import com.enterprise.auth.platform.common.outbox.OutboxEventTypes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class NotificationPublishOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final NotificationPublisher notificationPublisher;

    public NotificationPublishOutboxHandler(ObjectMapper objectMapper, NotificationPublisher notificationPublisher) {
        this.objectMapper = objectMapper;
        this.notificationPublisher = notificationPublisher;
    }

    @Override
    public String eventType() {
        return OutboxEventTypes.NOTIFICATION_PUBLISH;
    }

    @Override
    public void handle(OutboxEventEnvelope event) throws Exception {
        notificationPublisher.publish(objectMapper.readValue(event.payloadJson(), NotificationPublishCommand.class));
    }
}
