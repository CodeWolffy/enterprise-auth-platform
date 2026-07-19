package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.common.TimeSupport;
import com.enterprise.auth.platform.common.outbox.OutboxEventEnvelope;
import com.enterprise.auth.platform.common.outbox.OutboxEventHandler;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysOutboxEventMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Outbox 有界消费者：定时认领 PENDING 事件并投递到通知/邮件。
 */
@Component
public class OutboxDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchWorker.class);
    private static final int BATCH_SIZE = 40;

    private final OutboxEventClaimService claimService;
    private final SysOutboxEventMapper outboxEventMapper;
    private final OutboxPayloadProtectionService payloadProtectionService;
    private final Map<String, OutboxEventHandler> handlers;
    private final AtomicBoolean dispatching = new AtomicBoolean();

    public OutboxDispatchWorker(
            OutboxEventClaimService claimService,
            SysOutboxEventMapper outboxEventMapper,
            OutboxPayloadProtectionService payloadProtectionService,
            List<OutboxEventHandler> handlers
    ) {
        this.claimService = claimService;
        this.outboxEventMapper = outboxEventMapper;
        this.payloadProtectionService = payloadProtectionService;
        this.handlers = indexHandlers(handlers);
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    public void poll() {
        dispatchBatch();
    }

    @Async("notificationExecutor")
    public void triggerAsync() {
        dispatchBatch();
    }

    public void dispatchBatch() {
        // @Scheduled and after-write async triggers can overlap in one process. One dispatcher is enough;
        // database row claiming still protects deployments with multiple application instances.
        if (!dispatching.compareAndSet(false, true)) {
            return;
        }
        try {
            Instant now = TimeSupport.now();
            List<SysOutboxEventEntity> claimedEvents;
            try {
                claimedEvents = claimService.claimBatch(now, BATCH_SIZE);
            } catch (DeadlockLoserDataAccessException ex) {
                // Another worker may be claiming/recovering the same rows. The
                // next scheduled poll retries after the failed transaction is
                // fully rolled back; never issue more SQL in this transaction.
                log.debug("Outbox 认领遇到并发死锁，本轮跳过并等待下次轮询。error={}", ex.getMessage());
                return;
            }
            for (SysOutboxEventEntity event : claimedEvents) {
                try {
                    dispatchOne(event);
                    outboxEventMapper.markDone(event.getId());
                } catch (Exception ex) {
                    handleFailure(event, ex);
                }
            }
        } finally {
            dispatching.set(false);
        }
    }

    private void dispatchOne(SysOutboxEventEntity event) throws Exception {
        String type = event.getEventType();
        String payloadJson = payloadProtectionService.reveal(type, event.getPayloadJson());
        OutboxEventHandler handler = handlers.get(type);
        if (handler == null) {
            throw new IllegalArgumentException("unknown outbox event type: " + type);
        }
        handler.handle(new OutboxEventEnvelope(
                event.getId(),
                event.getTenantId(),
                type,
                event.getAggregateType(),
                event.getAggregateId(),
                payloadJson
        ));
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

    private Map<String, OutboxEventHandler> indexHandlers(List<OutboxEventHandler> handlerList) {
        Map<String, OutboxEventHandler> indexed = new LinkedHashMap<>();
        for (OutboxEventHandler handler : handlerList) {
            if (handler == null || !StringUtils.hasText(handler.eventType())) {
                throw new IllegalStateException("outbox handler event type must not be blank");
            }
            String type = handler.eventType().trim();
            if (indexed.putIfAbsent(type, handler) != null) {
                throw new IllegalStateException("duplicate outbox handler for event type: " + type);
            }
        }
        return Map.copyOf(indexed);
    }

    private String limit(String value, int max) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
