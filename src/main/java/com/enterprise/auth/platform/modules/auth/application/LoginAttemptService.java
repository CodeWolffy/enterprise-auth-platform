package com.enterprise.auth.platform.modules.auth.application;

import com.enterprise.auth.platform.common.RateLimitSupport;
import com.enterprise.auth.platform.infrastructure.config.RateLimitProperties;
import com.enterprise.auth.platform.modules.log.application.LogPublisher;
import com.enterprise.auth.platform.modules.log.domain.event.LoginLogEvent;
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
    private final LogPublisher logPublisher;
    private final SecurityPolicyApplicationService securityPolicyApplicationService;

    public LoginAttemptService(
            StringRedisTemplate stringRedisTemplate,
            RateLimitSupport rateLimitSupport,
            RateLimitProperties rateLimitProperties,
            LogPublisher logPublisher,
            SecurityPolicyApplicationService securityPolicyApplicationService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitSupport = rateLimitSupport;
        this.rateLimitProperties = rateLimitProperties;
        this.logPublisher = logPublisher;
        this.securityPolicyApplicationService = securityPolicyApplicationService;
    }

    public boolean isLocked(String tenantId, String username) {
        if (!rateLimitProperties.enabled()) {
            return false;
        }
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(lockKey(tenantId, username)));
    }

    public void recordBlockedAttempt(String tenantId, String username, String clientIp,
                                      String browser, String os, String location) {
        logPublisher.publish(new LoginLogEvent(username, tenantId, "LOCKED", "账户已锁定",
                clientIp, location, browser, os));
    }

    public LoginFailureResult recordFailure(String tenantId, String username, String reason, String clientIp,
                                             String browser, String os, String location) {
        if (!rateLimitProperties.enabled()) {
            logPublisher.publish(new LoginLogEvent(username, tenantId, "FAILED", reason,
                    clientIp, location, browser, os));
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
            logPublisher.publish(new LoginLogEvent(username, tenantId, "LOCKED", "账户已锁定",
                    clientIp, location, browser, os));
            return new LoginFailureResult(true, 0);
        }

        long remaining = Math.max(0, policy.loginFailureMaxAttempts() - (fails == null ? 0 : fails));
        logPublisher.publish(new LoginLogEvent(username, tenantId, "FAILED", reason,
                clientIp, location, browser, os));
        return new LoginFailureResult(false, remaining);
    }

    /**
     * 读取指定账号当前窗口内的登录失败次数(不含锁定态)。用于验证码风险升级判定。
     * 未启用限流或无记录时返回 0。
     */
    public long currentFailures(String tenantId, String username) {
        if (!rateLimitProperties.enabled() || tenantId == null || username == null || username.isBlank()) {
            return 0;
        }
        String value = stringRedisTemplate.opsForValue().get(failKey(tenantId, username));
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
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