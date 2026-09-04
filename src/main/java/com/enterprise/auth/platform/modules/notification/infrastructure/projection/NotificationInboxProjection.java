package com.enterprise.auth.platform.modules.notification.infrastructure.projection;

import java.time.Instant;
import lombok.Data;

/**
 * 通知收件箱统一分页投影，合并直接通知和系统广播通知。
 *
 * <p>广播通知使用负数 ID，保持现有 API 的标识语义；广播原始字段由
 * {@code NotificationView} 负责转换为兼容响应。</p>
 */
@Data
public class NotificationInboxProjection {

    private Long id;
    private Integer broadcastFlag;
    private String scenarioCode;
    private String sourceType;
    private String sourceId;
    private String bizType;
    private String bizId;
    private String title;
    private String content;
    private String level;
    private String link;
    private String actionPayloadJson;
    private String metadataJson;
    private Instant readAt;
    private Instant expiresAt;
    private Instant createdAt;
    private String noticeTitle;
    private String noticeContent;
    private Instant publishTime;

    public boolean isBroadcast() {
        return Integer.valueOf(1).equals(broadcastFlag);
    }
}
