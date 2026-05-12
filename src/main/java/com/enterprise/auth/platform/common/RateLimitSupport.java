package com.enterprise.auth.platform.common;

import com.enterprise.auth.platform.config.SecurityRedisProperties;
import java.time.Duration;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RateLimitSupport {

    private final SecurityRedisProperties redisProperties;

    public RateLimitSupport(SecurityRedisProperties redisProperties) {
        this.redisProperties = redisProperties;
    }

    public String buildKey(String namespace, String... parts) {
        StringBuilder sb = new StringBuilder(redisProperties.resolvedNamespacePrefix());
        sb.append(namespace);
        for (String part : parts) {
            sb.append(':').append(normalizeKeyPart(part));
        }
        return sb.toString();
    }

    public long incrementWithExpiry(StringRedisTemplate template, String key, Duration ttl) {
        Long current = template.opsForValue().increment(key);
        if (current != null && current == 1L) {
            template.expire(key, ttl);
        }
        return current == null ? 0L : current;
    }

    public String normalizeKeyPart(String value) {
        if (!StringUtils.hasText(value)) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}