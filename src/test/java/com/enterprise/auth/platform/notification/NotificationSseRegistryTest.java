package com.enterprise.auth.platform.notification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.enterprise.auth.platform.modules.notification.application.NotificationSseRegistry;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseRegistryTest {

    @Test
    void heartbeatShouldDiscardBrokenEmitterWithoutCompletingItFromSchedulerThread() {
        NotificationSseRegistry registry = new NotificationSseRegistry();
        BrokenEmitter emitter = new BrokenEmitter();

        emitterMap(registry).put("tenant-a:1", ConcurrentHashMap.newKeySet());
        emitterMap(registry).get("tenant-a:1").add(emitter);

        assertDoesNotThrow(registry::heartbeat);
        assertEquals(0, registry.activeConnectionCount());
        assertEquals(0, emitter.completeWithErrorCalls);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Set<SseEmitter>> emitterMap(NotificationSseRegistry registry) {
        return (Map<String, Set<SseEmitter>>) ReflectionTestUtils.getField(registry, "emittersByUser");
    }

    private static final class BrokenEmitter extends SseEmitter {

        private int completeWithErrorCalls = 0;

        private BrokenEmitter() {
            super(0L);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IllegalStateException("async request already failed");
        }

        @Override
        public void completeWithError(Throwable ex) {
            completeWithErrorCalls++;
            throw new AssertionError("completeWithError should not be called for broken SSE emitters", ex);
        }
    }
}
