package com.enterprise.auth.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.modules.auth.application.PasswordResetNotificationService;
import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.system.application.MailChannelApplicationService;
import com.enterprise.auth.platform.modules.system.application.MailChannelSenderManager;
import com.enterprise.auth.platform.modules.system.application.TransactionalMailSupport;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

class PasswordResetNotificationServiceTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(PasswordResetNotificationService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void logChannelInDevMustNotLeakRawTokenOrFullLink() {
        PasswordResetNotificationService service = newService(new MockEnvironment());
        String rawToken = "super-secret-reset-token-abc123";
        String resetLink = "http://localhost:5777/#/reset-password?token=" + rawToken;

        service.sendPasswordResetLink("platform", "u@example.com", "admin", resetLink);

        String allLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertThat(allLogs).doesNotContain(rawToken);
        assertThat(allLogs).doesNotContain("token=" + rawToken);
        assertThat(allLogs).doesNotContain(resetLink);
        assertThat(allLogs).contains("tokenFingerprint=");
    }

    @Test
    void stagingWithoutMailChannelMustFailClosed() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("staging");
        PasswordResetNotificationService service = newService(env);

        assertThatThrownBy(() -> service.sendPasswordResetLink(
                "platform",
                "u@example.com",
                "admin",
                "http://example/#/reset-password?token=secret"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("禁止通过日志下发");
    }

    private PasswordResetNotificationService newService(Environment environment) {
        MailChannelApplicationService mailChannelService = mock(MailChannelApplicationService.class);
        when(mailChannelService.getEnabledChannel(any())).thenReturn(Optional.empty());
        SecurityProperties props = new SecurityProperties(
                Duration.ofDays(7),
                Duration.ofMinutes(1),
                false,
                "Lax",
                new SecurityProperties.PasswordReset(
                        Duration.ofMinutes(10),
                        "http://localhost:5777/#/reset-password",
                        15,
                        3,
                        15,
                        10
                ),
                new SecurityProperties.Notification("log", "noreply@local"),
                new SecurityProperties.Redis(true, true, true, "eap:auth:", "v2", Duration.ofSeconds(30))
        );
        return new PasswordResetNotificationService(
                props,
                mailChannelService,
                mock(MailChannelSenderManager.class),
                mock(TransactionalMailSupport.class),
                environment
        );
    }
}