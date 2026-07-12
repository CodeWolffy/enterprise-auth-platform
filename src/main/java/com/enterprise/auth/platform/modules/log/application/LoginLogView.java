package com.enterprise.auth.platform.modules.log.application;

import java.time.Instant;

/** 登录日志列表视图。 */
public record LoginLogView(
        Long id,
        String tenantId,
        String userName,
        String status,
        String clientIp,
        String location,
        String browser,
        String os,
        String msg,
        Instant createdAt
) {
}