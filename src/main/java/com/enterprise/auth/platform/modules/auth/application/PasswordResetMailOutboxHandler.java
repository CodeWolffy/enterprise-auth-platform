package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.outbox.OutboxEventEnvelope;
import com.enterprise.auth.platform.common.outbox.OutboxEventHandler;
import com.enterprise.auth.platform.common.outbox.OutboxEventTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PasswordResetMailOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;
    private final PasswordResetNotificationService passwordResetNotificationService;

    public PasswordResetMailOutboxHandler(
            ObjectMapper objectMapper,
            PasswordResetNotificationService passwordResetNotificationService
    ) {
        this.objectMapper = objectMapper;
        this.passwordResetNotificationService = passwordResetNotificationService;
    }

    @Override
    public String eventType() {
        return OutboxEventTypes.PASSWORD_RESET_MAIL;
    }

    @Override
    public void handle(OutboxEventEnvelope event) throws Exception {
        JsonNode node = objectMapper.readTree(event.payloadJson());
        passwordResetNotificationService.sendPasswordResetLink(
                text(node, "tenantId"),
                text(node, "email"),
                text(node, "username"),
                text(node, "resetLink")
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value : null;
    }
}
