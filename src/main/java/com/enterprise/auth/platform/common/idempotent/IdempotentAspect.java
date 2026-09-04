package com.enterprise.auth.platform.common.idempotent;

import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

/**
 * 接口幂等防重切面。
 *
 * <p>拦截标注有 {@link Idempotent} 的方法，使用 Redisson 分布式锁控制互斥与重复提交。
 * 执行业务方法期间定期续租显式租期，避免慢请求超过固定租期后出现并发执行。</p>
 */
@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final String IDEMPOTENT_LOCK_PREFIX = "eap:idempotent:";
    private static final String FAILURE_MODE_OPEN = "open";
    private static final ScheduledExecutorService LEASE_RENEWAL_EXECUTOR =
            createLeaseRenewalExecutor();

    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final boolean failOpen;

    @Autowired
    public IdempotentAspect(
            ObjectProvider<RedissonClient> redissonClientProvider,
            @Value("${app.security.redis.idempotent-failure-mode:closed}") String failureMode) {
        this.redissonClientProvider = redissonClientProvider;
        this.failOpen = FAILURE_MODE_OPEN.equalsIgnoreCase(failureMode == null ? "" : failureMode.trim());
    }

    /**
     * 供不启动 Spring 容器的单元测试和嵌入式调用使用，默认采用生产安全的失败关闭策略。
     */
    public IdempotentAspect(ObjectProvider<RedissonClient> redissonClientProvider) {
        this(redissonClientProvider, "closed");
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            if (failOpen) {
                log.warn(
                        "RedissonClient unavailable; bypassing idempotent protection for method: {}",
                        joinPoint.getSignature().toShortString());
                return joinPoint.proceed();
            }
            log.error(
                    "RedissonClient unavailable; refusing idempotent-protected method: {}",
                    joinPoint.getSignature().toShortString());
            throw new BusinessException(
                    "IDEMPOTENCY_UNAVAILABLE",
                    "幂等保护暂不可用，请稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        String lockKey = buildLockKey(joinPoint, idempotent);
        RLock lock = redissonClient.getLock(lockKey);
        RBucket<Object> lockExpiry = redissonClient.getBucket(lockKey);
        validateTimeout(idempotent);

        boolean acquired;
        try {
            acquired = lock.tryLock(
                    idempotent.waitTime(), idempotent.timeout(), idempotent.timeUnit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("CONFLICT", "请求处理中断，请重试", HttpStatus.CONFLICT);
        }

        if (!acquired) {
            log.warn("Idempotent lock collision on key: {}", lockKey);
            throw new BusinessException("CONFLICT", idempotent.message(), HttpStatus.CONFLICT);
        }

        ScheduledFuture<?> renewal = scheduleLeaseRenewal(lock, lockExpiry, idempotent, Thread.currentThread().getId());
        try {
            return joinPoint.proceed();
        } finally {
            renewal.cancel(false);
            if (idempotent.releaseOnSuccess()) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.error("Failed to release idempotent lock: {}", lockKey, e);
                }
            } else {
                // 窗口从业务方法完成时重新开始，而不是从获取锁时开始计算。
                resetLeaseAfterExecution(lock, lockExpiry, idempotent, lockKey);
            }
        }
    }

    private static ScheduledExecutorService createLeaseRenewalExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(2, new DaemonThreadFactory());
        executor.setRemoveOnCancelPolicy(true);
        return executor;
    }

    private ScheduledFuture<?> scheduleLeaseRenewal(
            RLock lock, RBucket<Object> lockExpiry, Idempotent idempotent, long ownerThreadId) {
        long leaseMillis = Math.max(1, idempotent.timeUnit().toMillis(idempotent.timeout()));
        long intervalMillis = Math.max(1, Math.min(1000, leaseMillis / 3));
        return LEASE_RENEWAL_EXECUTOR.scheduleAtFixedRate(
                () -> renewLease(lock, lockExpiry, idempotent, ownerThreadId),
                intervalMillis,
                intervalMillis,
                TimeUnit.MILLISECONDS);
    }

    private void renewLease(
            RLock lock, RBucket<Object> lockExpiry, Idempotent idempotent, long ownerThreadId) {
        try {
            // 防止调度延迟导致旧 owner 已失去锁后误刷新新 owner 的租期。
            if (lock.isHeldByThread(ownerThreadId)) {
                lockExpiry.expire(idempotent.timeout(), idempotent.timeUnit());
            }
        } catch (Exception exception) {
            log.warn("Failed to renew idempotent lock lease", exception);
        }
    }

    private void resetLeaseAfterExecution(
            RLock lock, RBucket<Object> lockExpiry, Idempotent idempotent, String lockKey) {
        try {
            if (lock.isHeldByCurrentThread()
                    && !lockExpiry.expire(idempotent.timeout(), idempotent.timeUnit())) {
                log.warn("Failed to reset idempotent lock lease after execution: {}", lockKey);
            }
        } catch (Exception exception) {
            log.warn("Failed to reset idempotent lock lease after execution: {}", lockKey, exception);
        }
    }

    private void validateTimeout(Idempotent idempotent) {
        if (idempotent.timeout() <= 0) {
            throw new BusinessException("VALIDATION_ERROR", "幂等锁租期必须大于 0");
        }
    }

    private String buildLockKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object[] args = joinPoint.getArgs();

        StringBuilder keyBuilder = new StringBuilder(IDEMPOTENT_LOCK_PREFIX);

        if (idempotent.withTenant()) {
            String tenantId = TenantContextSupport.currentTenantIdTrimmedOr(TenantContextSupport.PLATFORM_TENANT_ID);
            keyBuilder.append(tenantId).append(":");
        }

        if (StringUtils.hasText(idempotent.prefix())) {
            keyBuilder.append(idempotent.prefix().trim()).append(":");
        } else {
            keyBuilder.append(method.getDeclaringClass().getSimpleName()).append(":").append(method.getName()).append(":");
        }

        if (StringUtils.hasText(idempotent.key())) {
            String parsedKey = parseSpelKey(method, args, idempotent.key());
            keyBuilder.append(parsedKey);
        } else {
            // 默认拼接所有入参哈希值
            String paramsHash = DigestUtils.md5DigestAsHex(Arrays.deepToString(args).getBytes(StandardCharsets.UTF_8));
            keyBuilder.append(paramsHash);
        }

        return keyBuilder.toString();
    }

    private String parseSpelKey(Method method, Object[] args, String spel) {
        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        if (paramNames != null && paramNames.length > 0) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        try {
            Object val = parser.parseExpression(spel).getValue(context);
            return val != null ? String.valueOf(val) : "null";
        } catch (Exception e) {
            log.error("Failed to parse SpEL expression '{}' on method '{}'", spel, method.getName(), e);
            throw new BusinessException("VALIDATION_ERROR", "幂等标识解析异常");
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "idempotent-lock-renewal");
            thread.setDaemon(true);
            return thread;
        }
    }
}
