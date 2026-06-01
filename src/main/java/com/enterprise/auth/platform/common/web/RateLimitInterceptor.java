package com.enterprise.auth.platform.common.web;

import cn.dev33.satoken.stp.StpUtil;
import com.enterprise.auth.platform.infrastructure.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final String LIMITER_UNAVAILABLE_MESSAGE = "限流服务暂不可用，请稍后重试";

    private final RateLimitProperties properties;
    private final LettuceBasedProxyManager<String> proxyManager;
    private final RedisClient redisClient;
    private final ClientIpResolver clientIpResolver;

    @Autowired
    public RateLimitInterceptor(
            RateLimitProperties properties,
            RedisProperties redisProperties,
            ClientIpResolver clientIpResolver
    ) {
        this(properties, tryBuildInfrastructure(redisProperties), clientIpResolver);
    }

    private RateLimitInterceptor(
            RateLimitProperties properties,
            RedisInfrastructure infrastructure,
            ClientIpResolver clientIpResolver
    ) {
        this(properties,
                infrastructure.proxyManager(),
                infrastructure.redisClient(),
                clientIpResolver);
    }

    RateLimitInterceptor(
            RateLimitProperties properties,
            @Nullable LettuceBasedProxyManager<String> proxyManager,
            @Nullable RedisClient redisClient,
            ClientIpResolver clientIpResolver
    ) {
        this.properties = properties;
        this.proxyManager = proxyManager;
        this.redisClient = redisClient;
        this.clientIpResolver = clientIpResolver;
    }

    private static RedisInfrastructure tryBuildInfrastructure(RedisProperties redisProperties) {
        try {
            return buildInfrastructure(redisProperties);
        } catch (Exception e) {
            log.warn("限流 Redis 初始化失败，应用继续启动并按限流 failure-mode 处理请求: {}", e.getMessage());
            return RedisInfrastructure.unavailable();
        }
    }

    private static RedisInfrastructure buildInfrastructure(RedisProperties redisProperties) {
        RedisClient redisClient = RedisClient.create(buildRedisURI(redisProperties));
        try {
            RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(codec);
            LettuceBasedProxyManager<String> proxyManager = LettuceBasedProxyManager.builderFor(connection)
                    .withClientSideConfig(ClientSideConfig.getDefault()
                            .withExpirationAfterWriteStrategy(
                                    ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10))
                            ))
                    .build();
            return new RedisInfrastructure(proxyManager, redisClient);
        } catch (RuntimeException e) {
            redisClient.shutdown();
            throw e;
        }
    }

    private static RedisURI buildRedisURI(RedisProperties redisProperties) {
        String host = redisProperties.getHost();
        if (!StringUtils.hasText(host) || isUnresolvedPlaceholder(host)) {
            throw new IllegalStateException("Redis host is not configured");
        }

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host.trim())
                .withPort(redisProperties.getPort())
                .withDatabase(redisProperties.getDatabase());

        String password = redisProperties.getPassword();
        if (StringUtils.hasText(password) && !isUnresolvedPlaceholder(password)) {
            builder.withPassword(password.toCharArray());
        }

        return builder.build();
    }

    private static boolean isUnresolvedPlaceholder(String value) {
        String trimmed = value.trim();
        return trimmed.startsWith("${") && trimmed.endsWith("}");
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

        String rateLimitKey = resolveRateLimitKey(rateLimit);
        String bucketKey = buildBucketKey(request, rateLimitKey, rateLimit);
        BucketConfiguration bucketConfig = resolveBucketConfig(rateLimitKey, rateLimit);
        Supplier<BucketConfiguration> configSupplier = () -> bucketConfig;
        long fallbackRetryAfterSeconds = resolveRefillDurationSeconds(rateLimitKey, rateLimit);

        try {
            if (proxyManager == null) {
                throw new IllegalStateException("Rate limit proxy manager is not initialized");
            }
            BucketProxy bucket = proxyManager.builder().build(bucketKey, configSupplier);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
                return true;
            }

            long retryAfterSeconds = nanosToSeconds(probe.getNanosToWaitForRefill(), fallbackRetryAfterSeconds);
            log.warn("请求被限流 {} {} key={} retryAfter={}s", request.getMethod(), request.getRequestURI(), bucketKey, retryAfterSeconds);
            throw new RateLimitExceededException(rateLimit.message(), retryAfterSeconds);
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (RedisException e) {
            return handleRateLimitInfrastructureFailure(rateLimitKey, request, fallbackRetryAfterSeconds, e);
        } catch (Exception e) {
            return handleRateLimitInfrastructureFailure(rateLimitKey, request, fallbackRetryAfterSeconds, e);
        }
    }

    String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private boolean handleRateLimitInfrastructureFailure(
            String rateLimitKey,
            HttpServletRequest request,
            long retryAfterSeconds,
            Exception exception
    ) {
        RateLimitProperties.FailureMode failureMode = properties.resolveFailureMode(rateLimitKey);
        if (failureMode == RateLimitProperties.FailureMode.CLOSED) {
            log.error("限流依赖异常，按 fail-closed 拒绝请求 {} {} key={}: {}",
                    request.getMethod(), request.getRequestURI(), rateLimitKey, exception.getMessage(), exception);
            throw new RateLimitExceededException(LIMITER_UNAVAILABLE_MESSAGE, retryAfterSeconds);
        }

        log.error("限流依赖异常，按 fail-open 放行请求 {} {} key={}: {}",
                request.getMethod(), request.getRequestURI(), rateLimitKey, exception.getMessage(), exception);
        return true;
    }

    private String buildBucketKey(HttpServletRequest request, String rateLimitKey, RateLimit rateLimit) {
        String ip = resolveClientIp(request);
        String userId = StpUtil.isLogin() ? String.valueOf(StpUtil.getLoginId()) : "anonymous";

        return switch (rateLimit.strategy()) {
            case IP -> "ratelimit:" + rateLimitKey + ":ip:" + ip;
            case USER -> "ratelimit:" + rateLimitKey + ":user:" + userId;
            case USER_AND_IP -> "ratelimit:" + rateLimitKey + ":user:" + userId + ":ip:" + ip;
        };
    }

    private String resolveRateLimitKey(RateLimit rateLimit) {
        return rateLimit.key().isEmpty() ? "default" : rateLimit.key();
    }

    private BucketConfiguration resolveBucketConfig(String rateLimitKey, RateLimit rateLimit) {
        int capacity = resolveIntValue(rateLimit.capacity(), resolveRuleCapacity(rateLimitKey), properties.resolvedDefaultCapacity());
        int refillTokens = resolveIntValue(rateLimit.refillTokens(), resolveRuleRefillTokens(rateLimitKey), properties.resolvedDefaultRefillTokens());
        long refillDurationSeconds = resolveRefillDurationSeconds(rateLimitKey, rateLimit);

        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(refillDurationSeconds))
                .build();

        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }

    private int resolveRuleCapacity(String rateLimitKey) {
        return properties.resolveRule(rateLimitKey)
                .map(RateLimitProperties.LimitRule::capacity)
                .orElse(-1);
    }

    private int resolveRuleRefillTokens(String rateLimitKey) {
        return properties.resolveRule(rateLimitKey)
                .map(RateLimitProperties.LimitRule::refillTokens)
                .orElse(-1);
    }

    private long resolveRefillDurationSeconds(String rateLimitKey, RateLimit rateLimit) {
        long ruleDurationSeconds = properties.resolveRule(rateLimitKey)
                .map(RateLimitProperties.LimitRule::refillDuration)
                .filter(duration -> duration != null)
                .map(Duration::getSeconds)
                .orElse(-1L);
        return resolveLongValue(
                rateLimit.refillDurationSeconds(),
                ruleDurationSeconds,
                properties.resolvedDefaultRefillDuration().getSeconds()
        );
    }

    private long nanosToSeconds(long nanosToWaitForRefill, long fallbackRetryAfterSeconds) {
        if (nanosToWaitForRefill <= 0) {
            return Math.max(1L, fallbackRetryAfterSeconds);
        }
        return Math.max(1L, Duration.ofNanos(nanosToWaitForRefill).toSeconds());
    }

    private int resolveIntValue(int annotationValue, int ruleValue, int defaultValue) {
        if (annotationValue > 0) {
            return annotationValue;
        }
        if (ruleValue > 0) {
            return ruleValue;
        }
        return defaultValue;
    }

    private long resolveLongValue(long annotationValue, long ruleValue, long defaultValue) {
        if (annotationValue > 0) {
            return annotationValue;
        }
        if (ruleValue > 0) {
            return ruleValue;
        }
        return defaultValue;
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final long retryAfterSeconds;

        public RateLimitExceededException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
        }

        public long retryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private record RedisInfrastructure(
            LettuceBasedProxyManager<String> proxyManager,
            RedisClient redisClient
    ) {
        static RedisInfrastructure unavailable() {
            return new RedisInfrastructure(null, null);
        }
    }
}