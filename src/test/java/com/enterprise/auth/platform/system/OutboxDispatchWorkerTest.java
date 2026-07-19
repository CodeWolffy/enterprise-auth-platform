package com.enterprise.auth.platform.system;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.auth.application.PasswordResetNotificationService;
import com.enterprise.auth.platform.modules.auth.application.PasswordResetMailOutboxHandler;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublisher;
import com.enterprise.auth.platform.modules.notification.application.NotificationPublishOutboxHandler;
import com.enterprise.auth.platform.modules.system.application.OutboxDispatchWorker;
import com.enterprise.auth.platform.modules.system.application.OutboxEventClaimService;
import com.enterprise.auth.platform.modules.system.application.OutboxPayloadProtectionService;
import com.enterprise.auth.platform.modules.system.application.OutboxWriter;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysOutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DeadlockLoserDataAccessException;

class OutboxDispatchWorkerTest {

    @Test
    void dispatchShouldClaimBeforeExternalDeliveryAndThenMarkDone() {
        OutboxEventClaimService claimService = mock(OutboxEventClaimService.class);
        SysOutboxEventMapper mapper = mock(SysOutboxEventMapper.class);
        NotificationPublisher publisher = mock(NotificationPublisher.class);
        PasswordResetNotificationService mailService = mock(PasswordResetNotificationService.class);
        OutboxPayloadProtectionService payloadProtectionService = mock(OutboxPayloadProtectionService.class);
        SysOutboxEventEntity event = notificationEvent();
        when(claimService.claimBatch(any(Instant.class), eq(40))).thenReturn(List.of(event));
        when(payloadProtectionService.reveal(event.getEventType(), event.getPayloadJson()))
                .thenReturn(event.getPayloadJson());
        OutboxDispatchWorker worker = new OutboxDispatchWorker(
                claimService,
                mapper,
                payloadProtectionService,
                List.of(new NotificationPublishOutboxHandler(
                        new ObjectMapper().findAndRegisterModules(),
                        publisher
                ))
        );

        worker.dispatchBatch();

        InOrder order = inOrder(claimService, publisher, mapper);
        order.verify(claimService).claimBatch(any(Instant.class), eq(40));
        order.verify(publisher).publish(any());
        order.verify(mapper).markDone(501L);
        verifyNoInteractions(mailService);
    }

    @Test
    void encryptedPasswordResetPayloadShouldBeRevealedOnlyForDelivery() {
        OutboxEventClaimService claimService = mock(OutboxEventClaimService.class);
        SysOutboxEventMapper mapper = mock(SysOutboxEventMapper.class);
        NotificationPublisher publisher = mock(NotificationPublisher.class);
        PasswordResetNotificationService mailService = mock(PasswordResetNotificationService.class);
        OutboxPayloadProtectionService payloadProtectionService = mock(OutboxPayloadProtectionService.class);
        SysOutboxEventEntity event = new SysOutboxEventEntity();
        event.setId(502L);
        event.setEventType(OutboxWriter.TYPE_PASSWORD_RESET_MAIL);
        event.setAttempts(0);
        event.setMaxAttempts(8);
        event.setPayloadJson("{enc}outbox:v1:ciphertext");
        when(claimService.claimBatch(any(Instant.class), eq(40))).thenReturn(List.of(event));
        when(payloadProtectionService.reveal(event.getEventType(), event.getPayloadJson())).thenReturn("""
                {"tenantId":"tenant-a","email":"u@example.com","username":"user-a",
                 "resetLink":"https://example/reset?token=raw-token"}
                """);
        OutboxDispatchWorker worker = new OutboxDispatchWorker(
                claimService,
                mapper,
                payloadProtectionService,
                List.of(new PasswordResetMailOutboxHandler(
                        new ObjectMapper().findAndRegisterModules(),
                        mailService
                ))
        );

        worker.dispatchBatch();

        verify(mailService).sendPasswordResetLink(
                "tenant-a",
                "u@example.com",
                "user-a",
                "https://example/reset?token=raw-token"
        );
        verify(mapper).markDone(502L);
        verifyNoInteractions(publisher);
    }

    @Test
    void deadlockDuringClaimShouldSkipThisPollAndRetryLater() {
        OutboxEventClaimService claimService = mock(OutboxEventClaimService.class);
        SysOutboxEventMapper mapper = mock(SysOutboxEventMapper.class);
        OutboxPayloadProtectionService payloadProtectionService = mock(OutboxPayloadProtectionService.class);
        when(claimService.claimBatch(any(Instant.class), eq(40)))
                .thenThrow(new DeadlockLoserDataAccessException("deadlock", new SQLException("1213")));
        OutboxDispatchWorker worker = new OutboxDispatchWorker(
                claimService,
                mapper,
                payloadProtectionService,
                List.of()
        );

        worker.dispatchBatch();

        verify(claimService).claimBatch(any(Instant.class), eq(40));
        verifyNoInteractions(mapper, payloadProtectionService);
    }

    private SysOutboxEventEntity notificationEvent() {
        SysOutboxEventEntity event = new SysOutboxEventEntity();
        event.setId(501L);
        event.setEventType(OutboxWriter.TYPE_NOTIFICATION_PUBLISH);
        event.setAttempts(0);
        event.setMaxAttempts(8);
        event.setPayloadJson("{\"tenantId\":\"tenant-a\",\"title\":\"test\"}");
        return event;
    }
}
