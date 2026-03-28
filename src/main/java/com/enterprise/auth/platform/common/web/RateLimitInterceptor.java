package com.enterprise.auth.platform.common.web;

import com.enterprise.auth.platform.common.annotation.RateLimit;
import com.enterprise.auth.platform.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
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
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@ConditionalOnProperty(prefix = "app.rate-limit", name = "enabled", havingValue = "true")
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);
    private static final String LIMITER_UNAVAILABLE_MESSAGE = "请求频率控制暂不可用，请稍后重试";

    private final RateLimitProperties properties;
    private final LettuceBasedProxyManager<String> proxyManager;
    private final RedisClient redisClient;
    private final List<IpAddressMatcher> trustedProxyMatchers;

    public RateLimitInterceptor(
            RateLimitProperties properties,
            RedisProperties redisProperties
    ) {
        this(properties, buildInfrastructure(redisProperties));
    }

    private RateLimitInterceptor(
            RateLimitProperties properties,
            RedisInfrastructure infrastructure
    ) {
        this(properties, infrastructure.proxyManager(), infrastructure.redisClient());
    }

    RateLimitInterceptor(
            RateLimitProperties properties,
            @Nullable LettuceBasedProxyManager<String> proxyManager,
            @Nullable RedisClient redisClient
    ) {
        this.properties = properties;
        this.proxyManager = proxyManager;
        this.redisClient = redisClient;
        this.trustedProxyMatchers = properties.resolvedTrustedProxies().stream()
                .map(IpAddressMatcher::new)
                .toList();
    }

    private static RedisInfrastructure buildInfrastructure(RedisProperties redisProperties) {
        RedisClient redisClient = RedisClient.create(buildRedisURI(redisProperties));
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(codec);
        LettuceBasedProxyManager<String> proxyManager = LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10))
                )
                .build();
        return new RedisInfrastructure(proxyManager, redisClient);
    }

    private static RedisURI buildRedisURI(RedisProperties redisProperties) {
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

    boolean isTrustedProxy(String remoteAddr) {
        if (!StringUtils.hasText(remoteAddr) || trustedProxyMatchers.isEmpty()) {
            return false;
        }
        return trustedProxyMatchers.stream().anyMatch(matcher -> matcher.matches(remoteAddr.trim()));
    }

    String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = StringUtils.hasText(request.getRemoteAddr()) ? request.getRemoteAddr().trim() : "unknown";
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String forwardedClientIp = xff.split(",")[0].trim();
            if (StringUtils.hasText(forwardedClientIp)) {
                return forwardedClientIp;
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return remoteAddr;
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String userId = authentication != null ? authentication.getName() : "anonymous";

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
        RateLimitProperties.LimitRule rule = properties.resolveRule(rateLimitKey);
        return rule == null ? -1 : rule.capacity();
    }

    private int resolveRuleRefillTokens(String rateLimitKey) {
        RateLimitProperties.LimitRule rule = properties.resolveRule(rateLimitKey);
        return rule == null ? -1 : rule.refillTokens();
    }

    private long resolveRefillDurationSeconds(String rateLimitKey, RateLimit rateLimit) {
        RateLimitProperties.LimitRule rule = properties.resolveRule(rateLimitKey);
        return resolveLongValue(
                rateLimit.refillDurationSeconds(),
                rule != null && rule.refillDuration() != null ? rule.refillDuration().getSeconds() : -1,
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
    }
}
