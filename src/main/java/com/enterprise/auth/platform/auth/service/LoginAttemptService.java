package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.config.security.SecurityRedisProperties;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LoginAttemptService {

    private static final long MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate stringRedisTemplate;
    private final SecurityRedisProperties redisProperties;
    private final AuditService auditService;

    public LoginAttemptService(
            StringRedisTemplate stringRedisTemplate,
            SecurityRedisProperties redisProperties,
            AuditService auditService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisProperties = redisProperties;
        this.auditService = auditService;
    }

    public boolean isLocked(String tenantId, String username) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(tenantId, username)));
    }

    public void recordBlockedAttempt(String tenantId, String username, String clientIp) {
        auditService.record("LOGIN_BLOCKED", username, tenantId,
                Map.of("reason", "account_locked", "clientIp", clientIp));
    }

    public LoginFailureResult recordFailure(String tenantId, String username, String reason, String clientIp) {
        String failKey = failKey(tenantId, username);
        String lockKey = lockKey(tenantId, username);
        Long fails = stringRedisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1) {
            stringRedisTemplate.expire(failKey, LOGIN_FAILURE_WINDOW);
        }

        if (fails != null && fails >= MAX_LOGIN_FAILURES) {
            stringRedisTemplate.opsForValue().set(lockKey, "LOCKED", LOGIN_FAILURE_WINDOW);
            stringRedisTemplate.delete(failKey);
            auditService.record("ACCOUNT_LOCKED", username, tenantId,
                    Map.of("reason", "exceed_max_failures", "clientIp", clientIp));
            return new LoginFailureResult(true, 0);
        }

        long remaining = Math.max(0, MAX_LOGIN_FAILURES - (fails == null ? 0 : fails));
        auditService.record("LOGIN_FAILED", username, tenantId, Map.of("reason", reason, "clientIp", clientIp));
        return new LoginFailureResult(false, remaining);
    }

    public void clearFailures(String tenantId, String username) {
        stringRedisTemplate.delete(failKey(tenantId, username));
        stringRedisTemplate.delete(lockKey(tenantId, username));
    }

    private String failKey(String tenantId, String username) {
        return redisProperties.resolvedNamespacePrefix()
                + "login:failure:"
                + normalizeKeyPart(tenantId)
                + ":"
                + normalizeKeyPart(username);
    }

    private String lockKey(String tenantId, String username) {
        return redisProperties.resolvedNamespacePrefix()
                + "login:lock:"
                + normalizeKeyPart(tenantId)
                + ":"
                + normalizeKeyPart(username);
    }

    private String normalizeKeyPart(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    public record LoginFailureResult(boolean locked, long remainingAttempts) {
    }
}
