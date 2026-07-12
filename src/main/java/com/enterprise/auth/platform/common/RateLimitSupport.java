package com.enterprise.auth.platform.common;

import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 限流 Redis key 工具。key 前缀来自配置，不再依赖 auth SecurityProperties。
 */
@Component
public class RateLimitSupport {

    private final String namespacePrefix;

    public RateLimitSupport(
            @Value("${app.security.redis.key-prefix:eap:auth:}") String keyPrefix,
            @Value("${app.security.redis.namespace-version:v1}") String namespaceVersion
    ) {
        String prefix = StringUtils.hasText(keyPrefix) ? keyPrefix.trim() : "eap:auth:";
        if (!prefix.endsWith(":")) {
            prefix = prefix + ":";
        }
        String version = StringUtils.hasText(namespaceVersion) ? namespaceVersion.trim() : "v1";
        this.namespacePrefix = prefix + version + ":";
    }

    public String buildKey(String namespace, String... parts) {
        StringBuilder sb = new StringBuilder(namespacePrefix);
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