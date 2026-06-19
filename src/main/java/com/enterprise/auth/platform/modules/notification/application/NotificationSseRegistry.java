package com.enterprise.auth.platform.modules.notification.application;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 站内通知 SSE 连接注册中心。
 * <p>
 * 单实例内存实现：维护每个租户+用户维度的活跃 SSE 连接，发布通知时实时推送到对应连接。
 * 多实例部署时需要引入 Redis Pub/Sub 或消息中间件做跨节点广播，当前 MVP 阶段暂不处理。
 */
@Component
public class NotificationSseRegistry {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseRegistry.class);
    private static final long SSE_TIMEOUT = 0L; // 永不超时，由心跳和客户端断开驱动清理

    private final Map<String, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /**
     * 为指定租户+用户注册一个新的 SSE 连接。
     */
    public SseEmitter register(String tenantId, Long userId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String key = key(tenantId, userId);
        emittersByUser.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(key, emitter);
        });
        emitter.onError(error -> remove(key, emitter));
        return emitter;
    }

    /**
     * 向指定租户+用户的所有活跃连接推送一条通知。
     */
    public void send(String tenantId, Long userId, NotificationView notification) {
        Set<SseEmitter> emitters = emittersByUser.get(key(tenantId, userId));
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(notification));
            } catch (IOException | IllegalStateException ex) {
                remove(key(tenantId, userId), emitter);
                emitter.completeWithError(ex);
            }
        }
    }

    /**
     * 向所有活跃连接发送心跳，保持连接不被代理或浏览器断开。
     */
    public void heartbeat() {
        for (Map.Entry<String, Set<SseEmitter>> entry : emittersByUser.entrySet()) {
            Set<SseEmitter> emitters = entry.getValue();
            if (emitters == null || emitters.isEmpty()) {
                continue;
            }
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data("ping"));
                } catch (IOException | IllegalStateException ex) {
                    remove(entry.getKey(), emitter);
                    emitter.completeWithError(ex);
                }
            }
        }
    }

    /**
     * 定时心跳：每 30 秒向所有 SSE 连接发送一次 ping，避免被反向代理或浏览器静默断开。
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void scheduledHeartbeat() {
        if (emittersByUser.isEmpty()) {
            return;
        }
        heartbeat();
    }

    /**
     * 返回当前活跃连接总数，便于监控与诊断。
     */
    public int activeConnectionCount() {
        return emittersByUser.values().stream().mapToInt(Set::size).sum();
    }

    private void remove(String key, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByUser.get(key);
        if (emitters == null) {
            return;
        }
        boolean removed = emitters.remove(emitter);
        if (removed && emitters.isEmpty()) {
            emittersByUser.remove(key, emitters);
        }
    }

    private String key(String tenantId, Long userId) {
        return tenantId + ":" + userId;
    }
}
