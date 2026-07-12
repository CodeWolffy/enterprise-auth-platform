package com.enterprise.auth.platform.modules.log.application;

import com.enterprise.auth.platform.common.event.SecurityAccessDeniedEvent;
import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 将 common 层安全拒绝事件转为审计日志，避免 GlobalExceptionHandler 反向依赖 log 模块。
 */
@Component
public class SecurityAccessDeniedLogListener {

    private final LogPublisher logPublisher;

    public SecurityAccessDeniedLogListener(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    @EventListener
    public void onSecurityAccessDenied(SecurityAccessDeniedEvent event) {
        if (event == null) {
            return;
        }
        logPublisher.publish(new LogEvent(
                "拒绝访问",
                event.operator(),
                event.tenantId(),
                event.details(),
                null,
                event.clientIp(),
                event.location(),
                event.method(),
                event.path(),
                null,
                null,
                null,
                null
        ));
    }
}