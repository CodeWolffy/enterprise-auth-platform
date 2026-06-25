package com.enterprise.auth.platform.modules.log.application;

import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import com.enterprise.auth.platform.modules.log.domain.event.LoginLogEvent;

public interface LogPublisher {

    void publish(LogEvent event);

    void publish(LoginLogEvent event);

    default void publish(String type, String operator, String tenantId, java.util.Map<String, Object> details) {
        publish(LogEvent.of(type, operator, tenantId, details));
    }
}