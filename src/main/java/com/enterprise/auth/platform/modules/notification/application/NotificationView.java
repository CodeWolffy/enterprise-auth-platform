package com.enterprise.auth.platform.modules.notification.application;

import com.enterprise.auth.platform.modules.notification.infrastructure.entity.SysUserNotificationEntity;
import com.enterprise.auth.platform.modules.notification.infrastructure.projection.NoticeBroadcastProjection;
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

    static NotificationView fromBroadcast(NoticeBroadcastProjection notice) {
        Long noticeId = notice.getId();
        return new NotificationView(
                noticeId == null ? null : -noticeId,
                "SYSTEM_NOTICE_PUBLISHED",
                "SYSTEM_NOTICE",
                noticeId == null ? null : String.valueOf(noticeId),
                "SYSTEM_NOTICE",
                noticeId == null ? null : String.valueOf(noticeId),
                "系统公告：" + fallback(notice.getNoticeTitle(), "公告"),
                limit(stripHtml(notice.getNoticeContent()), 500),
                "INFO",
                noticeId == null ? "/notices" : "/notices/" + noticeId,
                noticeId == null ? "{\"route\":\"/notices\"}" : "{\"route\":\"/notices\",\"noticeId\":" + noticeId + "}",
                noticeId == null ? "{}" : "{\"noticeId\":" + noticeId + "}",
                notice.getReadAt() != null,
                notice.getReadAt(),
                null,
                notice.getPublishTime() == null ? notice.getCreatedAt() : notice.getPublishTime()
        );
    }

    static NotificationView systemNoticeBroadcast(Long noticeId, String title, String content, Instant publishedAt) {
        NoticeBroadcastProjection projection = new NoticeBroadcastProjection();
        projection.setId(noticeId);
        projection.setNoticeTitle(title);
        projection.setNoticeContent(content);
        projection.setPublishTime(publishedAt);
        projection.setCreatedAt(publishedAt);
        return fromBroadcast(projection);
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private static String stripHtml(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
