package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.RateLimitSupport;
import com.enterprise.auth.platform.infrastructure.config.RateLimitProperties;
import com.enterprise.auth.platform.modules.audit.application.AuditService;
import com.enterprise.auth.platform.modules.security.application.SecurityPolicyApplicationService;
import com.enterprise.auth.platform.modules.security.domain.EffectiveSecurityPolicy;
import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    private static final long MAX_LOGIN_FAILURES = 5;
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitSupport rateLimitSupport;
    private final RateLimitProperties rateLimitProperties;
    private final AuditService auditService;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;

    public LoginAttemptService(
            StringRedisTemplate stringRedisTemplate,
            RateLimitSupport rateLimitSupport,
            RateLimitProperties rateLimitProperties,
            AuditService auditService,
            SecurityPolicyApplicationService securityPolicyApplicationService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitSupport = rateLimitSupport;
        this.rateLimitProperties = rateLimitProperties;
        this.auditService = auditService;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
    }

    public boolean isLocked(String tenantId, String username) {
        if (!rateLimitProperties.enabled()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(tenantId, username)));
    }

    public void recordBlockedAttempt(String tenantId, String username, String clientIp) {
        auditService.record("LOGIN_BLOCKED", username, tenantId,
                Map.of("reason", "account_locked", "clientIp", clientIp));
    }

    public LoginFailureResult recordFailure(String tenantId, String username, String reason, String clientIp) {
        if (!rateLimitProperties.enabled()) {
            auditService.record("LOGIN_FAILED", username, tenantId, Map.of("reason", reason, "clientIp", clientIp));
            return new LoginFailureResult(false, MAX_LOGIN_FAILURES);
        }

        EffectiveSecurityPolicy policy = securityPolicyApplicationService.effectivePolicy(tenantId);
        String failKey = failKey(tenantId, username);
        String lockKey = lockKey(tenantId, username);
        Long fails = stringRedisTemplate.opsForValue().increment(failKey);
        if (fails != null && fails == 1) {
            stringRedisTemplate.expire(failKey, Duration.ofMinutes(policy.loginFailureWindowMinutes()));
        }

        if (fails != null && fails >= policy.loginFailureMaxAttempts()) {
            stringRedisTemplate.opsForValue().set(lockKey, "LOCKED", Duration.ofMinutes(policy.loginFailureLockMinutes()));
            stringRedisTemplate.delete(failKey);
            auditService.record("ACCOUNT_LOCKED", username, tenantId,
                    Map.of("reason", "exceed_max_failures", "clientIp", clientIp));
            return new LoginFailureResult(true, 0);
        }

        long remaining = Math.max(0, policy.loginFailureMaxAttempts() - (fails == null ? 0 : fails));
        auditService.record("LOGIN_FAILED", username, tenantId, Map.of("reason", reason, "clientIp", clientIp));
        return new LoginFailureResult(false, remaining);
    }

    public void clearFailures(String tenantId, String username) {
        if (!rateLimitProperties.enabled()) {
            return;
        }
        stringRedisTemplate.delete(failKey(tenantId, username));
        stringRedisTemplate.delete(lockKey(tenantId, username));
    }

    private String failKey(String tenantId, String username) {
        return rateLimitSupport.buildKey("login:failure:", tenantId, username);
    }

    private String lockKey(String tenantId, String username) {
        return rateLimitSupport.buildKey("login:lock:", tenantId, username);
    }

    public record LoginFailureResult(boolean locked, long remainingAttempts) {
    }
}