package com.enterprise.auth.platform.common.web;

import com.enterprise.auth.platform.common.annotation.RateLimit;
import com.enterprise.auth.platform.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;

@Component
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    private final RateLimitProperties properties;
    private final LettuceBasedProxyManager<String> proxyManager;
    private final RedisClient redisClient;

    public RateLimitInterceptor(
            RateLimitProperties properties,
            RedisProperties redisProperties
    ) {
        this.properties = properties;

        RedisURI redisURI = buildRedisURI(redisProperties);
        this.redisClient = RedisClient.create(redisURI);

        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(codec);

        this.proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10))
                )
                .build();
    }

    private RedisURI buildRedisURI(RedisProperties redisProperties) {
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(redisProperties.getHost() != null ? redisProperties.getHost() : "127.0.0.1")
                .withPort(redisProperties.getPort())
                .withDatabase(redisProperties.getDatabase());

        if (StringUtils.hasText(redisProperties.getPassword())) {
            builder.withPassword(redisProperties.getPassword().toCharArray());
        }

        return builder.build();
    }

    @PreDestroy
    public void destroy() {
        if (redisClient != null) {
            try {
                redisClient.shutdown();
            } catch (Exception e) {
                log.warn("Redis client shutdown error: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        Method method = handlerMethod.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit == null) {
            Class<?> beanType = handlerMethod.getBeanType();
            rateLimit = beanType.getAnnotation(RateLimit.class);
        }

        if (rateLimit == null) {
            return true;
        }

        String bucketKey = buildBucketKey(request, rateLimit);
        BucketConfiguration bucketConfig = resolveBucketConfig(rateLimit);
        Supplier<BucketConfiguration> configSupplier = () -> bucketConfig;

        try {
            BucketProxy bucket = proxyManager.builder().build(bucketKey, configSupplier);

            if (bucket.tryConsume(1)) {
                long remainingTokens = bucket.getAvailableTokens();
                response.setHeader("X-RateLimit-Remaining", String.valueOf(remainingTokens));
                return true;
            }

            log.warn("请求被限流: {} {} key={}", request.getMethod(), request.getRequestURI(), bucketKey);
            throw new RateLimitExceededException(rateLimit.message());
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (RedisException e) {
            log.error("Redis 连接异常，跳过限流检查: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            log.error("限流检查异常，跳过限流: {}", e.getMessage(), e);
            return true;
        }
    }

    private String buildBucketKey(HttpServletRequest request, RateLimit rateLimit) {
        String keyPrefix = rateLimit.key().isEmpty() ? "default" : rateLimit.key();
        String ip = resolveClientIp(request);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication != null ? authentication.getName() : "anonymous";

        return switch (rateLimit.strategy()) {
            case IP -> "ratelimit:" + keyPrefix + ":ip:" + ip;
            case USER -> "ratelimit:" + keyPrefix + ":user:" + userId;
            case USER_AND_IP -> "ratelimit:" + keyPrefix + ":user:" + userId + ":ip:" + ip;
        };
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private BucketConfiguration resolveBucketConfig(RateLimit rateLimit) {
        String key = rateLimit.key();
        RateLimitProperties.LimitRule rule = properties.resolveRule(key);

        int capacity = resolveIntValue(rateLimit.capacity(), rule != null ? rule.capacity() : -1, properties.resolvedDefaultCapacity());
        int refillTokens = resolveIntValue(rateLimit.refillTokens(), rule != null ? rule.refillTokens() : -1, properties.resolvedDefaultRefillTokens());
        long refillDurationSeconds = resolveLongValue(
                rateLimit.refillDurationSeconds(),
                rule != null && rule.refillDuration() != null ? rule.refillDuration().getSeconds() : -1,
                properties.resolvedDefaultRefillDuration().getSeconds()
        );

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillDurationSeconds))
                .build();

        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }

    private int resolveIntValue(int annotationValue, int ruleValue, int defaultValue) {
        if (annotationValue > 0) return annotationValue;
        if (ruleValue > 0) return ruleValue;
        return defaultValue;
    }

    private long resolveLongValue(long annotationValue, long ruleValue, long defaultValue) {
        if (annotationValue > 0) return annotationValue;
        if (ruleValue > 0) return ruleValue;
        return defaultValue;
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }
}
