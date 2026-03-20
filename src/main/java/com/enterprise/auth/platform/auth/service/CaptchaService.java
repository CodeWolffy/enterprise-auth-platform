package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.lang.Nullable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CaptchaService {

    private final SecurityProperties securityProperties;
    private final SecurityRedisProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final Map<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();

    public CaptchaService(
            SecurityProperties securityProperties,
            SecurityRedisProperties redisProperties,
            @Nullable StringRedisTemplate redisTemplate,
            @Nullable RedissonClient redissonClient
    ) {
        this.securityProperties = securityProperties;
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
    }

    public CaptchaChallenge create() {
        String captchaId = UUID.randomUUID().toString();
        String answer = String.valueOf((int) (Math.random() * 9000) + 1000);
        Instant expiresAt = Instant.now().plus(securityProperties.captchaTtl());
        if (redissonCaptchaEnabled()) {
            try {
                redissonClient.getBucket(captchaKey(captchaId)).set(answer, securityProperties.captchaTtl());
            } catch (Exception ignored) {
                captchas.put(captchaId, new CaptchaEntry(answer, expiresAt));
            }
        } else if (redisCaptchaEnabled()) {
            try {
                redisTemplate.opsForValue().set(captchaKey(captchaId), answer, securityProperties.captchaTtl());
            } catch (Exception ignored) {
                captchas.put(captchaId, new CaptchaEntry(answer, expiresAt));
            }
        } else {
            captchas.put(captchaId, new CaptchaEntry(answer, expiresAt));
        }
        return new CaptchaChallenge(captchaId, expiresAt, securityProperties.exposeCaptchaAnswer() ? answer : null);
    }

    public void validate(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException("验证码不能为空");
        }
        String answer;
        if (redissonCaptchaEnabled()) {
            try {
                RBucket<String> bucket = redissonClient.getBucket(captchaKey(captchaId));
                answer = bucket.get();
                bucket.delete();
                if (!StringUtils.hasText(answer)) {
                    throw new BusinessException("验证码已过期");
                }
            } catch (Exception ignored) {
                CaptchaEntry entry = captchas.remove(captchaId);
                if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
                    throw new BusinessException("验证码已过期");
                }
                answer = entry.answer();
            }
        } else if (redisCaptchaEnabled()) {
            try {
                answer = redisTemplate.opsForValue().getAndDelete(captchaKey(captchaId));
                if (!StringUtils.hasText(answer)) {
                    throw new BusinessException("验证码已过期");
                }
            } catch (Exception ignored) {
                CaptchaEntry entry = captchas.remove(captchaId);
                if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
                    throw new BusinessException("验证码已过期");
                }
                answer = entry.answer();
            }
        } else {
            CaptchaEntry entry = captchas.remove(captchaId);
            if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
                throw new BusinessException("验证码已过期");
            }
            answer = entry.answer();
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
        return redisProperties.resolvedKeyPrefix() + "captcha:" + captchaId;
    }

    public record CaptchaChallenge(String captchaId, Instant expiresAt, String previewCode) {
    }

    private record CaptchaEntry(String answer, Instant expiresAt) {
    }
}
