package com.enterprise.auth.platform.service;

import com.enterprise.auth.platform.service.AuditService;
import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.common.RateLimitSupport;
import com.enterprise.auth.platform.config.RegistrationProperties;
import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RegisterAttemptService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitSupport rateLimitSupport;
    private final RegistrationProperties registrationProperties;
    private final RegistrationPolicyService registrationPolicyService;
    private final AuditService auditService;

    public RegisterAttemptService(
            StringRedisTemplate stringRedisTemplate,
            RateLimitSupport rateLimitSupport,
            RegistrationProperties registrationProperties,
            RegistrationPolicyService registrationPolicyService,
            AuditService auditService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rateLimitSupport = rateLimitSupport;
        this.registrationProperties = registrationProperties;
        this.registrationPolicyService = registrationPolicyService;
        this.auditService = auditService;
    }

    public void checkRateLimit(String username, String clientIp) {
        if (!registrationProperties.rateLimitEnabled()) {
            return;
        }

        Duration attemptWindow = registrationProperties.resolvedAttemptWindow();
        long userIpAttempts = rateLimitSupport.incrementWithExpiry(stringRedisTemplate,
                userIpKey(username, clientIp), attemptWindow);
        long ipAttempts = rateLimitSupport.incrementWithExpiry(stringRedisTemplate,
                ipKey(clientIp), attemptWindow);

        if (userIpAttempts <= registrationProperties.resolvedMaxAttemptsPerUserIp()
                && ipAttempts <= registrationProperties.resolvedMaxAttemptsPerIp()) {
            return;
        }

        auditService.record("REGISTER_RATE_LIMITED",
                rateLimitSupport.normalizeKeyPart(username),
                registrationPolicyService.resolveDefaultTenantId(),
                Map.of(
                        "clientIp", rateLimitSupport.normalizeKeyPart(clientIp),
                        "username", rateLimitSupport.normalizeKeyPart(username),
                        "userIpAttempts", userIpAttempts,
                        "ipAttempts", ipAttempts,
                        "windowSeconds", attemptWindow.toSeconds()
                ));
        throw new BusinessException("REGISTER_RATE_LIMITED", "注册尝试过于频繁，请稍后再试");
    }

    private String userIpKey(String username, String clientIp) {
        return rateLimitSupport.buildKey("register:attempt:user-ip:", clientIp, username);
    }

    private String ipKey(String clientIp) {
        return rateLimitSupport.buildKey("register:attempt:ip:", clientIp);
    }
}