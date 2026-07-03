package com.enterprise.auth.platform.modules.notification.application;

import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import java.time.Instant;

public record NotificationView(
        Long id,
        String scenarioCode,
        String sourceType,
        String sourceId,
        String bizType,
        String bizId,
        String title,
        String content,
        String level,
        String link,
        String actionPayload,
        String metadata,
        boolean read,
        Instant readAt,
        Instant expiresAt,
        Instant createdAt
) {
    static NotificationView from(SysUserNotificationEntity entity) {
        return new NotificationView(
                entity.getId(),
                entity.getScenarioCode(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getBizType(),
                entity.getBizId(),
                entity.getTitle(),
                entity.getContent(),
                entity.getLevel(),
                entity.getLink(),
                entity.getActionPayloadJson(),
                entity.getMetadataJson(),
                entity.getReadAt() != null,
                entity.getReadAt(),
                entity.getExpiresAt(),
                entity.getCreatedAt()
        );
    }
}
