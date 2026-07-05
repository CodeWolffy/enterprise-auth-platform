package com.enterprise.auth.platform.modules.notification.infrastructure.projection;

import java.time.Instant;
import lombok.Data;

@Data
public class NoticeBroadcastProjection {

    private Long id;
    private String tenantId;
    private String noticeTitle;
    private String noticeContent;
    private Instant publishTime;
    private String createdBy;
    private Instant createdAt;
    private Instant readAt;
}
