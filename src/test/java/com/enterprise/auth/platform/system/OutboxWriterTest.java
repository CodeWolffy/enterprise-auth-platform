package com.enterprise.auth.platform.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.enterprise.auth.platform.modules.system.application.OutboxPayloadProtectionService;
import com.enterprise.auth.platform.modules.system.application.OutboxProperties;
import com.enterprise.auth.platform.modules.system.application.OutboxWriter;
import com.enterprise.auth.platform.modules.system.infrastructure.entity.SysOutboxEventEntity;
import com.enterprise.auth.platform.modules.system.infrastructure.mapper.SysOutboxEventMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

class OutboxWriterTest {

    @Test
    void passwordResetPayloadMustBeProtectedBeforeInsert() {
        SysOutboxEventMapper mapper = mock(SysOutboxEventMapper.class);
        OutboxPayloadProtectionService protectionService = new OutboxPayloadProtectionService(
                new OutboxProperties(2000L, "test-outbox-payload-secret-key-32-chars"),
                new MockEnvironment()
        );
        OutboxWriter writer = new OutboxWriter(mapper, new ObjectMapper(), protectionService);

        writer.enqueue(
                OutboxWriter.TYPE_PASSWORD_RESET_MAIL,
                "tenant-a",
                Map.of("resetLink", "https://example/reset?token=raw-secret-token")
        );

        ArgumentCaptor<SysOutboxEventEntity> eventCaptor = ArgumentCaptor.forClass(SysOutboxEventEntity.class);
        verify(mapper).insert(eventCaptor.capture());
        SysOutboxEventEntity event = eventCaptor.getValue();
        assertThat(event.getPayloadJson()).startsWith("{enc}outbox:v1:");
        assertThat(event.getPayloadJson()).doesNotContain("raw-secret-token");
    }
}
