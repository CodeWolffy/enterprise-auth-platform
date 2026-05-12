package com.enterprise.auth.platform.common.convention.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.enterprise.auth.platform.config.RateLimitProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimitInterceptorTest {

    @Test
    void shouldUseForwardedClientIpWhenRemoteAddrIsTrustedProxy() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                properties(List.of("127.0.0.1/32")),
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.24, 127.0.0.1");

        assertThat(interceptor.resolveClientIp(request)).isEqualTo("198.51.100.24");
    }

    @Test
    void shouldIgnoreForwardedClientIpWhenRemoteAddrIsNotTrustedProxy() {
        RateLimitInterceptor interceptor = new RateLimitInterceptor(
                properties(List.of("127.0.0.1/32")),
                null,
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.10");
        request.addHeader("X-Forwarded-For", "198.51.100.24");
        request.addHeader("X-Real-IP", "198.51.100.25");

        assertThat(interceptor.resolveClientIp(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void shouldNotFailStartupWhenRedisHostPlaceholderIsUnresolved() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("${REDIS_HOST}");
        redisProperties.setPort(6379);

        assertThatCode(() -> new RateLimitInterceptor(properties(List.of()), redisProperties))
                .doesNotThrowAnyException();
    }

    private RateLimitProperties properties(List<String> trustedProxies) {
        return new RateLimitProperties(
                true,
                20,
                20,
                Duration.ofMinutes(1),
                RateLimitProperties.FailureMode.OPEN,
                trustedProxies,
                null
        );
    }
}
