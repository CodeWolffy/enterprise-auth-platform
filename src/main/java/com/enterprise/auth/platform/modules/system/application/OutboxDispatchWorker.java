package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.modules.auth.application.PasswordResetNotificationService;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublishCommand;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublisher;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysOutboxEventMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Outbox 有界消费者：定时认领 PENDING 事件并投递到通知/邮件。
 */
@Component
public class OutboxDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchWorker.class);
    private static final int BATCH_SIZE = 40;

    private final SysOutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;
    private final NotificationPublisher notificationPublisher;
    private final PasswordResetNotificationService passwordResetNotificationService;

    public OutboxDispatchWorker(
            SysOutboxEventMapper outboxEventMapper,
            ObjectMapper objectMapper,
            NotificationPublisher notificationPublisher,
            PasswordResetNotificationService passwordResetNotificationService
    ) {
        this.outboxEventMapper = outboxEventMapper;
        this.objectMapper = objectMapper;
        this.notificationPublisher = notificationPublisher;
        this.passwordResetNotificationService = passwordResetNotificationService;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void poll() {
        dispatchBatch();
    }

    @Async("notificationExecutor")
    public void triggerAsync() {
        dispatchBatch();
    }

    @Transactional
    public void dispatchBatch() {
        Instant now = TimeSupport.now();
        List<SysOutboxEventEntity> candidates = outboxEventMapper.claimCandidates(now, BATCH_SIZE);
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (SysOutboxEventEntity event : candidates) {
            if (event == null || event.getId() == null) {
                continue;
            }
            if (outboxEventMapper.markProcessing(event.getId()) != 1) {
                continue;
            }
            try {
                dispatchOne(event);
                outboxEventMapper.markDone(event.getId());
            } catch (Exception ex) {
                handleFailure(event, ex);
            }
        }
    }

    private void dispatchOne(SysOutboxEventEntity event) throws Exception {
        String type = event.getEventType();
        if (OutboxWriter.TYPE_NOTIFICATION_PUBLISH.equals(type)) {
            NotificationPublishCommand command = objectMapper.readValue(
                    event.getPayloadJson(),
                    NotificationPublishCommand.class
            );
            notificationPublisher.publish(command);
            return;
        }
        if (OutboxWriter.TYPE_PASSWORD_RESET_MAIL.equals(type)) {
            JsonNode node = objectMapper.readTree(event.getPayloadJson());
            passwordResetNotificationService.sendPasswordResetLink(
                    text(node, "tenantId"),
                    text(node, "email"),
                    text(node, "username"),
                    text(node, "resetLink")
            );
            return;
        }
        throw new IllegalArgumentException("unknown outbox event type: " + type);
    }

    private void handleFailure(SysOutboxEventEntity event, Exception ex) {
        int attempts = event.getAttempts() == null ? 1 : event.getAttempts() + 1;
        int maxAttempts = event.getMaxAttempts() == null ? 8 : event.getMaxAttempts();
        String error = limit(ex.getMessage(), 900);
        if (attempts >= maxAttempts) {
            outboxEventMapper.markRetryOrDead(event.getId(), "DEAD", null, error);
            log.error("Outbox 事件进入死信。id={}, type={}, error={}", event.getId(), event.getEventType(), error);
            return;
        }
        // 指数退避：2^attempts 秒，上限 15 分钟
        long delaySeconds = Math.min(900L, 1L << Math.min(attempts, 10));
        Instant next = TimeSupport.now().plus(Duration.ofSeconds(delaySeconds));
        outboxEventMapper.markRetryOrDead(event.getId(), "PENDING", next, error);
        log.warn(
                "Outbox 投递失败将重试。id={}, type={}, attempts={}, nextRetryAt={}, error={}",
                event.getId(),
                event.getEventType(),
                attempts,
                next,
                error
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText();
        return StringUtils.hasText(value) ? value : null;
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}