package com.enterprise.auth.platform.common.event;

import java.util.Map;

/**
 * 安全拒绝访问事件：common 只发布事件，由 audit/log 模块消费落库。
 */
public record SecurityAccessDeniedEvent(
        String operator,
        String tenantId,
        String method,
        String path,
        String clientIp,
        String location,
        Map<String, Object> details
) {
    public SecurityAccessDeniedEvent {
        if (details == null) {
            details = Map.of();
        }
    }
}