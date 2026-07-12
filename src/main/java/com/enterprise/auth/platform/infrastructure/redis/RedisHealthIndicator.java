package com.enterprise.auth.platform.infrastructure.redis;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Redis 健康检查（/actuator/health 中的 "redis" 组件）。
 *
 * <p>优先使用 RedissonClient（若已装配）执行 {@code pingAsync()}；
 * 未装配时降级为 Spring RedisConnectionFactory 的 {@code ping()} 命令。
 * 探测在守护线程上执行并带 4s 超时保护，避免 Redis 无响应时拖垮整个 health 端点。</p>
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(4);

    private final ObjectProvider<RedissonClient> redissonClient;
    private final RedisConnectionFactory connectionFactory;
    private final RedisProperties redisProperties;
    private final ExecutorService probeExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "redis-health-probe");
        t.setDaemon(true);
        return t;
    });

    public RedisHealthIndicator(
            ObjectProvider<RedissonClient> redissonClient,
            RedisConnectionFactory connectionFactory,
            RedisProperties redisProperties
    ) {
        this.redissonClient = redissonClient;
        this.connectionFactory = connectionFactory;
        this.redisProperties = redisProperties;
    }

    @Override
    public Health health() {
        try {
            CompletableFuture<Health> future = CompletableFuture.supplyAsync(this::probe, probeExecutor);
            try {
                return future.get(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return Health.down()
                        .withDetail("error", "redis probe timed out after " + PROBE_TIMEOUT.toSeconds() + "s")
                        .build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("error", "redis probe interrupted").build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return down(cause);
        }
    }

    private Health probe() {
        RedissonClient client = redissonClient.getIfAvailable();
        if (client != null) {
            return probeViaRedisson(client);
        }
        return probeViaConnectionFactory();
    }

    private Health probeViaRedisson(RedissonClient client) {
        try {
            client.getNodesGroup().pingAll();
            return Health.up()
                    .withDetail("mode", "redisson")
                    .withDetail("host", redisProperties.getHost() + ":" + redisProperties.getPort())
                    .build();
        } catch (Exception e) {
            return down(e);
        }
    }

    private Health probeViaConnectionFactory() {
        try (var conn = connectionFactory.getConnection()) {
            String pong = conn.ping();
            if (!"PONG".equalsIgnoreCase(pong)) {
                return Health.down()
                        .withDetail("mode", "spring")
                        .withDetail("error", "unexpected ping response: " + pong)
                        .build();
            }
            return Health.up()
                    .withDetail("mode", "spring")
                    .withDetail("host", redisProperties.getHost() + ":" + redisProperties.getPort())
                    .build();
        } catch (Exception e) {
            return down(e);
        }
    }

    private Health down(Throwable cause) {
        String msg = cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        return Health.down().withDetail("error", msg).build();
    }

    @PreDestroy
    void shutdownProbeExecutor() {
        probeExecutor.shutdownNow();
    }
}
