package com.enterprise.auth.platform.modules.log.application;

import com.enterprise.auth.platform.modules.log.domain.event.LogEvent;
import com.enterprise.auth.platform.modules.log.domain.event.LoginLogEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class LogEventListener {

    private final LogPublisher logPublisher;

    public LogEventListener(LogPublisher logPublisher) {
        this.logPublisher = logPublisher;
    }

    @Async
    @EventListener
    public void handleLogEvent(LogEvent event) {
        logPublisher.publish(event);
    }

    @Async
    @EventListener
    public void handleLoginLogEvent(LoginLogEvent event) {
        logPublisher.publish(event);
    }
}
