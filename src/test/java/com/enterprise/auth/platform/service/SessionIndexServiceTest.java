package com.enterprise.auth.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.auth.platform.modules.auth.infrastructure.SecurityProperties;
import com.enterprise.auth.platform.modules.auth.application.SessionIndexService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Test
    @SuppressWarnings("unchecked")
    void countVisibleShouldFilterByTenantAndVisibleUsers() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        ZSetOperations<String, String> zSetOps = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(zSetOps.zCard("eap:test:v1:session:index:all")).thenReturn(3L);
        when(zSetOps.reverseRange("eap:test:v1:session:index:all", 0, 199L))
                .thenReturn(Set.of("token-1", "token-2", "token-3"));
        when(hashOps.entries("eap:test:v1:session:meta:token-1")).thenReturn(Map.of(
                "userId", "100",
                "username", "admin",
                "tenantId", "tenant-a",
                "activeTenantId", "tenant-a"
        ));
        when(hashOps.entries("eap:test:v1:session:meta:token-2")).thenReturn(Map.of(
                "userId", "200",
                "username", "visible",
                "tenantId", "tenant-a",
                "activeTenantId", "tenant-a"
        ));
        when(hashOps.entries("eap:test:v1:session:meta:token-3")).thenReturn(Map.of(
                "userId", "300",
                "username", "other",
                "tenantId", "tenant-b",
                "activeTenantId", "tenant-b"
        ));
        SessionIndexService service = new SessionIndexService(redisTemplate, securityProperties());

        Optional<Long> count = service.countVisible("tenant-a", false, Optional.of(Set.of(100L)));

        assertThat(count).contains(1L);
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