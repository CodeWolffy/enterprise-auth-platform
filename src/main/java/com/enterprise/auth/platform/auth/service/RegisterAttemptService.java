package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.audit.service.AuditService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.RegistrationProperties;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RegisterAttemptService {

    private final StringRedisTemplate stringRedisTemplate;
    private final SecurityRedisProperties redisProperties;
    private final RegistrationProperties registrationProperties;
    private final RegistrationPolicyService registrationPolicyService;
    private final AuditService auditService;

    public RegisterAttemptService(
            StringRedisTemplate stringRedisTemplate,
            SecurityRedisProperties redisProperties,
            RegistrationProperties registrationProperties,
            RegistrationPolicyService registrationPolicyService,
            AuditService auditService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisProperties = redisProperties;
        this.registrationProperties = registrationProperties;
        this.registrationPolicyService = registrationPolicyService;
        this.auditService = auditService;
    }

    public void checkRateLimit(String username, String clientIp) {
        if (!registrationProperties.rateLimitEnabled()) {
            return;
        }

        Duration attemptWindow = registrationProperties.resolvedAttemptWindow();
        long userIpAttempts = incrementWithExpiry(userIpKey(username, clientIp), attemptWindow);
        long ipAttempts = incrementWithExpiry(ipKey(clientIp), attemptWindow);

        if (userIpAttempts <= registrationProperties.resolvedMaxAttemptsPerUserIp()
                && ipAttempts <= registrationProperties.resolvedMaxAttemptsPerIp()) {
            return;
        }

        auditService.record("REGISTER_RATE_LIMITED", normalizeKeyPart(username), registrationPolicyService.resolveDefaultTenantId(),
                Map.of(
                        "clientIp", normalizeKeyPart(clientIp),
                        "username", normalizeKeyPart(username),
                        "userIpAttempts", userIpAttempts,
                        "ipAttempts", ipAttempts,
                        "windowSeconds", attemptWindow.toSeconds()
                ));
        throw new BusinessException("REGISTER_RATE_LIMITED", "注册尝试过于频繁，请稍后再试");
    }

    private long incrementWithExpiry(String key, Duration ttl) {
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current != null && current == 1L) {
            stringRedisTemplate.expire(key, ttl);
        }
        return current == null ? 0L : current;
    }

    private String userIpKey(String username, String clientIp) {
        return redisProperties.resolvedNamespacePrefix()
                + "register:attempt:user-ip:"
                + normalizeKeyPart(clientIp)
                + ":"
                + normalizeKeyPart(username);
    }

    private String ipKey(String clientIp) {
        return redisProperties.resolvedNamespacePrefix()
                + "register:attempt:ip:"
                + normalizeKeyPart(clientIp);
    }

    private String normalizeKeyPart(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}