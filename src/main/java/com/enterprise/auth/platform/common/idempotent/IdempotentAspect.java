package com.enterprise.auth.platform.common.idempotent;

import com.enterprise.auth.platform.common.context.TenantContextSupport;
import com.enterprise.auth.platform.common.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 接口幂等防重切面。
 * <p>
 * 拦截标注有 {@link Idempotent} 的方法，使用 Redisson 分布式锁控制互斥与重复提交。
 * </p>
 */
@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);
    private static final String IDEMPOTENT_LOCK_PREFIX = "eap:idempotent:";

    private final ObjectProvider<RedissonClient> redissonClientProvider;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public IdempotentAspect(ObjectProvider<RedissonClient> redissonClientProvider) {
        this.redissonClientProvider = redissonClientProvider;
    }

    @Around("@annotation(idempotent)")
    public Object around(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        RedissonClient redissonClient = redissonClientProvider.getIfAvailable();
        if (redissonClient == null) {
            // 若环境未启用 RedissonClient（如测试模式或关闭 redisson-enabled），直接放行
            log.debug("RedissonClient unavailable, bypassing idempotent check for method: {}", joinPoint.getSignature().toShortString());
            return joinPoint.proceed();
        }

        String lockKey = buildLockKey(joinPoint, idempotent);
        RLock lock = redissonClient.getLock(lockKey);

        boolean acquired = false;
        try {
            long waitTime = idempotent.waitTime();
            long leaseTime = idempotent.timeout();
            acquired = lock.tryLock(waitTime, leaseTime, idempotent.timeUnit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("CONFLICT", "请求处理中断，请重试", HttpStatus.CONFLICT);
        }

        if (!acquired) {
            log.warn("Idempotent lock collision on key: {}", lockKey);
            throw new BusinessException("CONFLICT", idempotent.message(), HttpStatus.CONFLICT);
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (idempotent.releaseOnSuccess()) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.error("Failed to release idempotent lock: {}", lockKey, e);
                }
            }
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
}
