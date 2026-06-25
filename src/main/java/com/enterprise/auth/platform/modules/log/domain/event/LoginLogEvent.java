package com.enterprise.auth.platform.modules.log.domain.event;

import java.util.Map;

public record LoginLogEvent(
        String operator,
        String tenantId,
        String status,
        String msg,
        String ipAddr,
        String location,
        String browser,
        String os
) {
}