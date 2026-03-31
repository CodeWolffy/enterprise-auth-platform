package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import java.time.Instant;
import java.util.UUID;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CaptchaService {

    private final SecurityProperties securityProperties;
    private final SecurityRedisProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final Environment environment;

    public CaptchaService(
            SecurityProperties securityProperties,
            SecurityRedisProperties redisProperties,
            @Nullable StringRedisTemplate redisTemplate,
            @Nullable RedissonClient redissonClient,
            Environment environment
    ) {
        this.securityProperties = securityProperties;
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.environment = environment;
    }

    public CaptchaChallenge create() {
        String captchaId = UUID.randomUUID().toString();
        String answer = String.valueOf((int) (Math.random() * 9000) + 1000);
        Instant expiresAt = Instant.now().plus(securityProperties.captchaTtl());

        if (redissonCaptchaEnabled()) {
            try {
                redissonClient.getBucket(captchaKey(captchaId)).set(answer, securityProperties.captchaTtl());
                return new CaptchaChallenge(captchaId, expiresAt, shouldExposePreview() ? answer : null);
            } catch (Exception ex) {
                throw new IllegalStateException("通过 Redisson 持久化验证码失败", ex);
            }
        }

        if (redisCaptchaEnabled()) {
            try {
                redisTemplate.opsForValue().set(captchaKey(captchaId), answer, securityProperties.captchaTtl());
                return new CaptchaChallenge(captchaId, expiresAt, shouldExposePreview() ? answer : null);
            } catch (Exception ex) {
                throw new IllegalStateException("通过 RedisTemplate 持久化验证码失败", ex);
            }
        }

        throw new IllegalStateException("验证码存储依赖 Redis，但 Redis 当前不可用");
    }

    public void validate(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException("验证码 ID 和验证码不能为空");
        }

        String answer;
        if (redissonCaptchaEnabled()) {
            try {
                RBucket<String> bucket = redissonClient.getBucket(captchaKey(captchaId));
                answer = bucket.get();
                bucket.delete();
            } catch (Exception ex) {
                throw new IllegalStateException("通过 Redisson 校验验证码失败", ex);
            }
        } else if (redisCaptchaEnabled()) {
            try {
                answer = redisTemplate.opsForValue().getAndDelete(captchaKey(captchaId));
            } catch (Exception ex) {
                throw new IllegalStateException("通过 RedisTemplate 校验验证码失败", ex);
            }
        } else {
            throw new IllegalStateException("验证码存储依赖 Redis，但 Redis 当前不可用");
        }

        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("验证码已过期");
        }
        if (!answer.equalsIgnoreCase(captchaCode)) {
            throw new BusinessException("验证码错误");
        }
    }

    private boolean redisCaptchaEnabled() {
        return redisProperties.captchaEnabled() && redisTemplate != null;
    }

    private boolean redissonCaptchaEnabled() {
        return redisProperties.captchaEnabled() && redisProperties.redissonEnabled() && redissonClient != null;
    }

    private String captchaKey(String captchaId) {
        return redisProperties.resolvedNamespacePrefix() + "captcha:id:" + captchaId;
    }

    private boolean shouldExposePreview() {
        return securityProperties.exposeCaptchaAnswer() || !environment.acceptsProfiles(Profiles.of("prod"));
    }

    public record CaptchaChallenge(String captchaId, Instant expiresAt, String previewCode) {
    }
}
