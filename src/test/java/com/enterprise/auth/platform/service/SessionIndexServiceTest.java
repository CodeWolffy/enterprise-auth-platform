package com.enterprise.auth.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.config.SecurityProperties;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

class SessionIndexServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void registerShouldPersistActiveTenantWithLoginTenant() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        service.register("token-1", 100L, "admin", "platform", "127.0.0.1", "browser", 1000L, 2000L);

        verify(hashOps).putAll(eq("eap:test:v1:session:meta:token-1"), argThat(meta -> {
            assertThat(meta).isInstanceOf(Map.class);
            return "platform".equals(meta.get("tenantId"))
                    && "platform".equals(meta.get("activeTenantId"));
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateActiveTenantShouldPersistActiveTenantOnly() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        service.updateActiveTenant("token-1", "tenant-a");

        verify(hashOps).put("eap:test:v1:session:meta:token-1", "activeTenantId", "tenant-a");
        verify(redisTemplate).expire("eap:test:v1:session:meta:token-1", Duration.ofDays(7).plusHours(1));
    }

    private SecurityProperties securityProperties() {
        return new SecurityProperties(
                Duration.ofDays(7),
                Duration.ofMinutes(1),
                false,
                "Lax",
                new SecurityProperties.Redis(true, false, false, "eap:test:", "v1")
        );
    }
}