package com.enterprise.auth.platform.modules.notification.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record NotificationPublishCommand(
        String tenantId,
        String scenarioCode,
        String sourceType,
        String sourceId,
        String bizType,
        String bizId,
        Set<Long> recipientUserIds,
        Set<String> recipientRoleCodes,
        boolean tenantAdmins,
        Map<String, Object> variables,
        String title,
        String content,
        String level,
        String link,
        Map<String, Object> actionPayload,
        Map<String, Object> metadata,
        String dedupKey,
        Instant expiresAt,
        String createdBy
) {
    public NotificationPublishCommand {
        recipientUserIds = copyUserIds(recipientUserIds);
        recipientRoleCodes = copyRoleCodes(recipientRoleCodes);
        level = level == null || level.isBlank() ? "INFO" : level.trim().toUpperCase();
        variables = variables == null ? Map.of() : Map.copyOf(variables);
        actionPayload = actionPayload == null ? Map.of() : Map.copyOf(actionPayload);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static Set<Long> copyUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> values = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                values.add(userId);
            }
        }
        return values;
    }

    private static Set<String> copyRoleCodes(Set<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String roleCode : roleCodes) {
            if (roleCode != null && !roleCode.isBlank()) {
                values.add(roleCode.trim());
            }
        }
        return values;
    }
}